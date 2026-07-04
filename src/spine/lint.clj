(ns spine.lint
  "Generalized prose and code lint. Does NOT require Vale. Two layers:

     1. House regex pre-pass over prose files (.md/.mdx/.txt): bans the
        em-dash character, ASCII arrows in prose, plan/task process IDs, and
        ASCII banner lines. Fence-aware for code blocks, and inline-code
        spans are stripped on every rule, so documenting a banned token
        (the em dash, the `->` macro, an ID format) inside backticks is exempt.

     2. A project linter, when one is detectable from the descriptor's :lanes
        or a common config file, run over its natural scope.

  Every finding is lifted into the canonical finding shape with
  :dimension :lint, so it joins the same findings/*.edn pool the reviewers
  write. Zero model tokens. Accepts --edn PATH to write the lifted findings
  as an EDN vector for agentic triage."
  (:require [spine.host :as host]
            [spine.repo :as repo]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private em-dash (str (char 0x2014)))

(def ^:private banner-re #"^\s*(={8,}|-{8,}|\*{8,}|#{8,}|~{8,})\s*$")
(def ^:private pid-re #"\b[pPtT]\d{2,}\b")

(defn- ascii-arrow?
  "True when s contains -> not preceded by - or < and not followed by -.
  Portable replacement for the lookbehind/lookahead regex
  (?<![-<])->(?!-) which not all regex engines support."
  [s]
  (loop [search s offset 0]
    (let [idx (str/index-of search "->")]
      (if (nil? idx)
        false
        (let [abs (+ offset idx)
              n (count s)
              prev (when (> abs 0) (subs s (dec abs) abs))
              nxt (when (< (+ abs 2) n) (subs s (+ abs 2) (+ abs 3)))]
          (if (and (not= prev "-") (not= prev "<")
                   (not= nxt "-"))
            true
            (recur (subs s (inc abs)) (inc abs))))))))

(defn- strip-inline-code
  "Remove `...` spans so documenting a token like the `->` macro or an ID
  format inside backticks does not trip the arrow or process-id rules."
  [line]
  (str/replace line #"`[^`]*`" ""))

(defn- prose-ext? [p]
  (let [ext (some-> (host/extension p) str/lower-case (str/replace #"^\." ""))]
    (contains? #{"md" "mdx" "txt"} ext)))

(defn- skip-dir? [parts]
  (some #(#{".git" ".jj" "state" ".opencode" "node_modules" "target" "_build"
            ".build"} (str %)) parts))

(defn- default-files
  "All prose files under root, excluding generated and dependency dirs.
  The **.ext form matches both top-level and nested files under root."
  [root]
  (->> (mapcat #(host/glob root (str "**" %)) [".md" ".mdx" ".txt"])
       (remove #(skip-dir? (host/components %)))
       vec))

;;;; house regex pre-pass

(defn- scan-lines
  "Return findings for one file. fence-aware for every rule; inline-code
  spans are stripped throughout (documenting a banned token in backticks
  is exempt). Each finding carries line + rule id."
  [file text]
  (let [lines (str/split-lines text)]
    (loop [i 0 in-fence false acc []]
      (if (>= i (count lines))
        acc ;; already in natural ascending line order (appended, not conj-fronted)
        (let [line (nth lines i)
              fence? (boolean (re-find #"^\s*```" line))
              now-fence (if fence? (not in-fence) in-fence)
              finds
              (cond-> []
                (str/includes? (strip-inline-code line) em-dash)
                (conj {:file file :line (inc i) :severity :MODERATE
                       :rule "prose/em-dash"
                       :evidence (str "Em-dash character is banned: "
                                      (str/trim line))})
                (and (not in-fence) (ascii-arrow? (strip-inline-code line)))
                (conj {:file file :line (inc i) :severity :MINOR
                       :rule "prose/ascii-arrow"
                       :evidence (str "ASCII arrow in prose is an AI tell: "
                                      (str/trim line))})
                (and (not in-fence) (re-find pid-re (strip-inline-code line)))
                (conj {:file file :line (inc i) :severity :MINOR
                       :rule "prose/process-id"
                       :evidence (str "Plan/task process ID is banned: "
                                      (str/trim line))})
                (and (not in-fence) (re-find banner-re line))
                (conj {:file file :line (inc i) :severity :MODERATE
                       :rule "prose/banner"
                       :evidence (str "ASCII banner line is banned: "
                                      (str/trim line))}))]
          (recur (inc i) now-fence (into acc finds)))))))

(defn house-scan
  "Run the house regex pre-pass over files (paths or strings). Returns
  findings in canonical shape."
  [files]
  (let [paths (map #(if (string? %) (host/path %) %) files)]
    (vec (mapcat (fn [p]
                   (when (and (host/exists? p) (prose-ext? p))
                     (scan-lines (host/path-str p) (slurp (host/path-str p)))))
                 paths))))

;;;; source-comment pre-pass

(def ^:private src-exts #{"clj" "cljc" "cljs" "ex" "exs" "zig" "c" "h" "cpp" "hpp" "cc"})

(defn- src-lang
  "The comment family for a source path: :clj (`;`), :hash (`#`, Elixir),
  :slash (`//`, C/Zig), or nil."
  [p]
  (let [ext (some-> (host/extension p) str/lower-case (str/replace #"^\." ""))]
    (cond (contains? #{"clj" "cljc" "cljs"} ext) :clj
          (contains? #{"ex" "exs"} ext) :hash
          (contains? #{"c" "h" "cpp" "hpp" "cc" "zig"} ext) :slash
          :else nil)))

(def ^:private src-comment-re
  {:clj   #"^\s*;+"
   :hash  #"^\s*#($|[^{!])"
   :slash #"^\s*//"})

(def ^:private src-banner-re
  {:clj   #"^\s*;+\s+-{3,}"
   :hash  #"^\s*#\s+-{3,}"
   :slash #"^\s*//\s+-{3,}"})

(def ^:private wall-threshold 4)

(defn- default-source-files
  "All source files under root whose comment family the scanner speaks."
  [root]
  (->> (mapcat #(host/glob root (str "**." %)) src-exts)
       (remove #(skip-dir? (host/components %)))
       vec))

(defn- banner-findings [lang file lines]
  (let [re (src-banner-re lang)]
    (into []
      (for [[i line] (map vector (range) lines)
            :when (re-find re line)]
        {:file file :line (inc i) :severity :MODERATE
         :rule "src/banner"
         :evidence (str "Decorated comment banner is banned; use a bare label line: "
                        (str/trim line))}))))

(defn- wall-findings
  "Inline comment walls: wall-threshold or more consecutive comment lines
  AFTER the file's leading header. Comments, blanks, and a shebang line
  before the first code line form the file-top header block, which is
  exempt (mirrors the C translation-unit block comment). Blank lines and
  code break a run."
  [lang file lines]
  (let [re (src-comment-re lang)]
    (letfn [(emit [start end]
              {:file file :line (inc start) :severity :MODERATE
               :rule "src/comment-wall"
               :evidence (str "Comment block of " (- end start)
                              " lines exceeds the 3-line budget")})]
      (loop [i 0 start nil seen-code? false acc []]
        (if (>= i (count lines))
          (cond-> acc (and start seen-code? (>= (- i start) wall-threshold))
                  (conj (emit start i)))
          (let [line (nth lines i)]
            (cond
              (re-find re line)
              (recur (inc i)
                     (if seen-code? (or start i) nil)
                     seen-code? acc)
              (or (str/blank? line) (re-find #"^\s*#!" line))
              (let [end-run? (and start seen-code?
                                  (>= (- i start) wall-threshold))]
                (recur (inc i) nil seen-code?
                       (if end-run? (conj acc (emit start i)) acc)))
              :else
              (let [end-run? (and start (>= (- i start) wall-threshold))]
                (recur (inc i) nil true
                       (if end-run? (conj acc (emit start i)) acc))))))))))

(defn scan-source
  "Banner and comment-wall findings for one source file's text. Accepts a
  path or path-str; returns nil when the comment family is unknown. Public
  for testing; zero model tokens."
  [p text]
  (let [path (if (string? p) (host/path p) p)
        file (host/path-str path)]
    (when-let [lang (src-lang path)]
      (let [lines (str/split-lines text)]
        (into [] (concat (banner-findings lang file lines)
                         (wall-findings lang file lines)))))))

(defn source-scan
  "Run the source-comment pre-pass over files (paths or strings). Returns
  findings in canonical shape."
  [files]
  (let [paths (map #(if (string? %) (host/path %) %) files)]
    (vec (mapcat (fn [p]
                   (when (host/exists? p)
                     (scan-source p (slurp (host/path-str p)))))
                 paths))))

(defn load-extra-rules
  "Read compiled banned-pattern rules (from agentic rules compile) under the
  working dir, when present, and scan the given prose files for them. Each
  rule is {:id :pattern :message :level}. One-way projection, deterministic."
  [root files]
  (let [rules (repo/read-edn (host/path (repo/working-dir root) "rules" "lint-rules.edn"))]
    (when rules
        (vec (for [pf (map str files)
                   :when (and (host/exists? pf) (prose-ext? pf))
                   line-idx (map vector (str/split-lines (slurp pf)) (range))
                   :let [line (first line-idx) ln (inc (second line-idx))]
                   r rules
                   :when (re-find (re-pattern (:pattern r)) line)]
               {:file pf :line ln
                :severity (keyword (str/upper-case (name (or (:level r) :minor))))
                :rule (str "decision/" (:id r))
                 :evidence (str (:message r) ": " (str/trim line))})))))


;;;; project linter detection and running

(def ^:private known-linters
  "first-token -> [config-flags matcher]. matcher parses one line of stdout
  into a finding map or nil."
  {"clj-kondo" :clj-kondo
   "credo"     :credo
   "clang-tidy" :grep
   "cppcheck"  :grep})

(defn- read-descriptor [root]
  (or (repo/read-edn (host/path root "project.edn"))
      {}))

(defn- which? [cmd] (boolean (host/which cmd)))

(defn- lane-commands [desc]
  (let [lanes (:lanes desc)]
    (if (map? lanes) (apply concat (vals lanes)) (vec lanes))))

(defn- detect-linter
  "Return {:tool :invoke} where :invoke is a zero-arg fn returning
  {:out ... :exit ...}, or nil. Detection: a known linter named in the
  descriptor lanes, or a known linter installed plus its config present."
  [root]
  (let [desc (read-descriptor root)
        lane-cmds (lane-commands desc)
        lane-tool (some (fn [c]
                          (let [tok (first (str/split (str c) #"\s+"))]
                            (when (known-linters tok) tok)))
                        lane-cmds)
        clj?  (or lane-tool
                  (and (which? "clj-kondo")
                       (or (host/exists? (host/path root ".clj-kondo"))
                           (host/exists? (host/path root "deps.edn"))
                           (host/exists? (host/path root "project.clj")))))
        credo? (and (which? "mix") (host/exists? (host/path root ".credo.exs")))]
    (cond
      (and clj? (not= credo? true))
      {:tool "clj-kondo"
       :invoke (fn [] (host/shell {:dir (host/path-str root)}
                                  "clj-kondo" "--lint" "." "--output-format" "edn"))}
      credo?
      {:tool "credo"
       :invoke (fn [] (host/shell {:dir (host/path-str root)}
                                  "mix" "credo" "--format" "flycheck"))}
      :else nil)))

(defn- grep-finding [file tool line-text]
  ;; "<file>:<line>[:<col>]: <severity>: <msg>" (credo/clang-tidy/cppcheck)
  (when-let [m (re-matches #"[^:]+:(\d+)(?::\d+)?:\s*(\w+):?\s*(.*)" line-text)]
    (let [ln (Integer/parseInt (second m))
          sev (str/lower-case (nth m 2))
          msg (nth m 3)]
      {:file file :line ln :severity (cond (#{"error"} sev) :SIGNIFICANT
                                          (#{"warning" "refactor" "consistency"} sev) :MODERATE
                                          :else :MINOR)
       :rule (str tool "/lint") :evidence msg})))

(defn run-project-linter
  "Run the detected project linter and lift findings. Returns nil when no
  linter is detectable (not an error; the house scan still runs)."
  [root]
  (when-let [l (detect-linter root)]
    (let [{:keys [out exit]} ((:invoke l))
          tool (:tool l)]
      (cond
        (= tool "clj-kondo")
        (try
          (let [data (edn/read-string out)
                findmap {:WARNING :MODERATE :ERROR :SIGNIFICANT}]
            (vec (for [f (:findings data)]
                   {:file (:filename f) :line (:row f 1)
                    :severity (findmap (:level f) :MINOR)
                    :rule (str "clj-kondo/" (:type f "lint"))
                    :evidence (:message f)})))
           (catch Exception _ []))
        (= tool "credo")
        (vec (keep #(grep-finding (:file (re-find #"^([^:]+):" %)) tool %)
                   (str/split-lines out)))
        :else []))))

;;;; canonical lifting and entry point

(defn as-finding
  "Lift a raw lint finding into the canonical shape (flat, :dimension :lint)."
  [f]
  (merge {:dimension :lint :suggestion nil :reporter "lint"} f))

(defn format-finding
  "The agreed line shape for stdout: file:line|SEVERITY|rule|evidence."
  [{:keys [file line severity rule evidence]}]
  (str file ":" (or line 0) "|" (name severity) "|" rule "|" evidence))

(defn -main
  "lint [--edn PATH] [FILE...] - prints findings in the line shape and,
  when --edn PATH is given, writes the lifted findings as an EDN vector for
  triage. With no FILE, scans all prose files under the project dir and runs
  the detected project linter. The project home (resolved from cwd basename)
  supplies the rules and descriptor. Exit 0 clean, 1 findings, 2 usage."
  [& args]
  (let [[edn-path files-raw] (if (= "--edn" (first args))
                               [(second args) (drop 2 args)]
                               [nil args])
        home (repo/project-home)
        scan-dir "."
         files (if (empty? files-raw)
                 (concat (default-files scan-dir) (default-source-files scan-dir))
                 files-raw)
         house (house-scan files)
         src   (source-scan files)
         extra (or (load-extra-rules home files) [])
         proj  (or (run-project-linter scan-dir) [])
         raw   (concat house src extra proj)
        findings (mapv as-finding raw)]
    (doseq [f findings] (println (format-finding f)))
    (when edn-path
      (repo/write-text! edn-path (pr-str findings)))
    (System/exit (if (seq findings) 1 0))))
