(ns spine.lint
  "Generalized prose and code lint. Does NOT require Vale. Two layers:

    1. House regex pre-pass over prose files (.md/.mdx/.txt): bans the
       em-dash character, ASCII arrows in prose, plan/task process IDs, and
       ASCII banner lines. Fence-aware so code blocks are exempt (the em-dash
       check is global; it is never legitimate in this codebase).

    2. A project linter, when one is detectable from the descriptor's :lanes
       or a common config file, run over its natural scope.

  Every finding is lifted into the canonical finding shape with
  :dimension :lint, so it joins the same findings/*.edn pool the reviewers
  write. Zero model tokens. Accepts --edn PATH to write the lifted findings
  as an EDN vector for bb triage."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [spine.core :as core]))

(def ^:private em-dash (str (char 0x2014)))

(def ^:private banner-re #"^\s*([=\-*#~])\1{7,}\s*$")
(def ^:private arrow-re #"(?<![-<])->(?!-)")
(def ^:private pid-re #"\b[pPtT]\d{2,}\b")

(defn- prose-ext? [p]
  (let [ext (some-> (fs/extension p) str/lower-case (str/replace #"^\." ""))]
    (contains? #{"md" "mdx" "txt"} ext)))

(defn- skip-dir? [parts]
  (some #(#{".git" ".jj" ".spine" ".opencode" "node_modules" "target" "_build"
            ".build"} (str %)) parts))

(defn- default-files
  "All prose files under root, excluding generated and dependency dirs.
  The **.ext form matches both top-level and nested files under root."
  [root]
  (->> (mapcat #(fs/glob root (str "**" %)) [".md" ".mdx" ".txt"])
       (remove #(skip-dir? (fs/components %)))
       vec))

;; --- house regex pre-pass ------------------------------------------------

(defn- scan-lines
  "Return findings for one file. fence-aware for the arrow, pid, and banner
  rules; the em-dash rule is global. Each finding carries line + rule id."
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
                (str/includes? line em-dash)
                (conj {:file file :line (inc i) :severity :MODERATE
                       :rule "prose/em-dash"
                       :evidence (str "Em-dash character is banned: "
                                      (str/trim line))})
                (and (not in-fence) (re-find arrow-re line))
                (conj {:file file :line (inc i) :severity :MINOR
                       :rule "prose/ascii-arrow"
                       :evidence (str "ASCII arrow in prose is an AI tell: "
                                      (str/trim line))})
                (and (not in-fence) (re-find pid-re line))
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
  (let [paths (map #(if (string? %) (fs/path %) %) files)]
    (vec (mapcat (fn [p]
                   (when (and (fs/exists? p) (prose-ext? p))
                     (scan-lines (str p) (slurp (str p)))))
                 paths))))

(defn load-extra-rules
  "Read compiled banned-pattern rules (from bb compile-rules) under the
  working dir, when present, and scan the given prose files for them. Each
  rule is {:id :pattern :message :level}. One-way projection, deterministic."
  [root files]
  (let [f (fs/path (core/working-dir root) "rules" "lint-rules.edn")]
    (when (fs/exists? f)
      (let [rules (edn/read-string (slurp (str f)))]
        (vec (for [pf (map str files)
                   :when (and (fs/exists? pf) (prose-ext? pf))
                   line-idx (map vector (str/split-lines (slurp pf)) (range))
                   :let [line (first line-idx) ln (inc (second line-idx))]
                   r rules
                   :when (re-find (re-pattern (:pattern r)) line)]
               {:file pf :line ln
                :severity (keyword (str/upper-case (name (or (:level r) :minor))))
                :rule (str "decision/" (:id r))
                :evidence (str (:message r) ": " (str/trim line))}))))))

;; --- project linter detection and running --------------------------------

(def ^:private known-linters
  "first-token -> [config-flags matcher]. matcher parses one line of stdout
  into a finding map or nil."
  {"clj-kondo" :clj-kondo
   "credo"     :credo
   "clang-tidy" :grep
   "cppcheck"  :grep})

(defn- read-descriptor [root]
  (or (core/read-edn (str (fs/path root ".claude" "project.edn")))
      (core/read-edn (str (fs/path root "project.edn")))
      {}))

(defn- which? [cmd] (boolean (fs/which cmd)))

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
                       (or (fs/exists? (fs/path root ".clj-kondo"))
                           (fs/exists? (fs/path root "deps.edn"))
                           (fs/exists? (fs/path root "project.clj")))))
        credo? (and (which? "mix") (fs/exists? (fs/path root ".credo.exs")))]
    (cond
      (and clj? (not= credo? true))
      {:tool "clj-kondo"
       :invoke (fn [] (p/shell {:out :string :err :string :continue true
                                :dir (str root)}
                               "clj-kondo" "--lint" "." "--output-format" "edn"))}
      credo?
      {:tool "credo"
       :invoke (fn [] (p/shell {:out :string :err :string :continue true
                                :dir (str root)}
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
        (let [data (edn/read-string out)
              findmap {:WARNING :MODERATE :ERROR :SIGNIFICANT}]
          (vec (for [f (:findings data)]
                 {:file (:filename f) :line (:row f 1)
                  :severity (findmap (:level f) :MINOR)
                  :rule (str "clj-kondo/" (:type f "lint"))
                  :evidence (:message f)})))
        (= tool "credo")
        (vec (keep #(grep-finding (:file (re-find #"^([^:]+):" %)) tool %)
                   (str/split-lines out)))
        :else []))))

;; --- canonical lifting and entry point -----------------------------------

(defn as-finding
  "Lift a raw lint finding into the canonical shape (flat, :dimension :lint)."
  [f]
  (merge {:dimension :lint :suggestion nil :reporter "lint"} f))

(defn format-finding
  "The agreed line shape for stdout: file:line|SEVERITY|rule|evidence."
  [{:keys [file line severity rule evidence]}]
  (str file ":" (or line 0) "|" (name severity) "|" rule "|" evidence))

(defn -main
  "bb lint [--edn PATH] [FILE...] - prints findings in the line shape and,
  when --edn PATH is given, writes the lifted findings as an EDN vector for
  bb triage. With no FILE, scans all prose files under the root and runs the
  detected project linter. Exit 0 clean, 1 findings, 2 usage."
  [& args]
  (let [[edn-path files-raw] (if (= "--edn" (first args))
                               [(second args) (drop 2 args)]
                               [nil args])
        root "."
        files (if (empty? files-raw) (default-files root) files-raw)
        house (house-scan files)
        extra (or (load-extra-rules root files) [])
        proj  (or (run-project-linter root) [])
        raw   (concat house extra proj)
        findings (mapv as-finding raw)]
    (doseq [f findings] (println (format-finding f)))
    (when edn-path
      (fs/create-dirs (fs/parent (fs/path edn-path)))
      (spit edn-path (pr-str findings)))
    (System/exit (if (seq findings) 1 0))))
