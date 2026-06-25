(ns spine.core
  "Shared spine helpers: EDN read/write, the canonical finding shape, the
  escalate-don't-guess writer, and a small jj-or-git VCS adapter (prefer
  jj). Software-only; no prose-production logic. Babashka-runnable, deps
  via bb.edn."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

;; --- working dir ---------------------------------------------------------

(defn working-dir
  "The spine working dir under root. Honors the project descriptor's
  :spine :working-dir when present, else defaults to .spine. Created on
  first write, never assumed to exist on read."
  [root]
  (let [desc-path (or (fs/path root ".claude" "project.edn")
                      (fs/path root "project.edn"))]
    (if (fs/exists? desc-path)
      (let [desc (edn/read-string (slurp (str desc-path)))]
        (fs/path root (or (get-in desc [:spine :working-dir]) ".spine")))
      (fs/path root ".spine"))))

;; --- EDN read/write ------------------------------------------------------

(defn read-edn
  "Parse an EDN file, nil if it does not exist. The single parser for the
  spine: code never parses its own rendered output, only these EDN files."
  [path]
  (let [p (fs/path path)]
    (when (fs/exists? p)
      (edn/read-string (slurp (str p))))))

(defn write-edn!
  "Write value as pretty-printed EDN to path, creating parent dirs. Stable
  shape for deterministic folds: same inputs, same bytes."
  [path value]
  (let [p (fs/path path)]
    (fs/create-dirs (fs/parent p))
    (spit (str p) (with-out-str (pprint/pprint value)))
    value))

(defn read-edn-in
  "Read an EDN file located by joining segments under root."
  [root & segments]
  (read-edn (apply str (fs/path root (map str segments)))))

;; --- canonical finding shape ---------------------------------------------

(def finding-keys
  "The canonical finding shape consumed by triage. Reporters and lint write
  maps with these keys (flat, not nested). :level is derived by triage from
  :dimension and need not be supplied by the reporter."
  [:dimension :severity :level :file :evidence :suggestion :rule])

(defn finding?
  "True when m carries the minimum a finding needs to be triaged: a
  dimension, a severity, a file, and some evidence."
  [m]
  (and (map? m)
       (:dimension m)
       (:severity m)
       (:file m)
       (not (str/blank? (str (:evidence m))))))

;; --- escalate, don't guess -----------------------------------------------

(defn- read-escalations [esc-path]
  (if (fs/exists? esc-path)
    (let [data (edn/read-string (slurp (str esc-path)))]
      (if (map? data) data {:escalations (vec data)}))
    {:escalations []}))

(defn escalate!
  "Append an entry to <working-dir>/escalation.edn instead of guessing.
  entry is a map describing the collision (e.g. two unequal values for one
  key). Returns the full escalations map. The orchestrator surfaces the
  count; a human resolves each one. Never silently pick."
  [root entry]
  (let [wd   (working-dir root)
        path (fs/path wd "escalation.edn")
        cur  (read-escalations path)
        nxt  (update cur :escalations (fnil conj []) entry)]
    (write-edn! (str path) nxt)))

(defn pending-collisions
  "Count of unresolved escalations on disk (0 when none)."
  [root]
  (count (:escalations (read-escalations (fs/path (working-dir root)
                                                   "escalation.edn")))))

;; --- VCS adapter: prefer jj, fall back to git ----------------------------

(defn jj-repo? [root] (fs/exists? (fs/path root ".jj")))
(defn git-repo? [root] (fs/exists? (fs/path root ".git")))

(defn detect-vcs
  " :jj when a jj repo is present (preferred, even when collocated with
  git), :git otherwise, nil when neither."
  [root]
  (cond (jj-repo? root) :jj (git-repo? root) :git :else nil))

(defn vcs-shell
  "Run cmd in root, return {:out :err :exit}. Never throws; callers branch
  on :exit."
  [root & cmd]
  (let [{:keys [out err exit]}
        (apply p/shell {:out :string :err :string :continue true
                        :dir (str root)}
               (map str cmd))]
    {:out (str/trim (or out "")) :err (str/trim (or err "")) :exit exit}))

(defn- git-branches
  [root prefix]
  (->> (:out (apply vcs-shell root "git" "for-each-ref"
                    "--format=%(refname:short)" "refs/heads"))
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (filter #(str/starts-with? % prefix))
       sort
       vec))

(defn- jj-bookmarks
  [root prefix]
  (->> (:out (vcs-shell root "jj" "bookmark" "list" "-T" "name ++ \"\n\""))
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (filter #(str/starts-with? % prefix))
       sort
       vec))

(defn vcs-adapter
  "Return a uniform map of VCS closures for root and detected vcs. All
  callers stay vcs-agnostic. Keys: :working, :branches, :commits,
  :land-branch, :delete. The jj path lands a whole bookmark with one
  rebase onto @; the git path cherry-picks each commit oldest-first."
  [root]
  (let [vcs (detect-vcs root)]
    (case vcs
      :git
      {:vcs     :git
       :working (fn [] (:out (vcs-shell root "git" "rev-parse" "--abbrev-ref" "HEAD")))
       :branches (fn [prefix] (git-branches root prefix))
       :commits  (fn [working branch]
                   (->> (:out (vcs-shell root "git" "rev-list" "--reverse"
                                              (str working ".." branch)))
                        str/split-lines (map str/trim) (remove str/blank?) vec))
       :land-branch
       (fn [_working branch commits]
         (vec
          (for [c commits]
            (let [{:keys [exit err]} (vcs-shell root "git" "cherry-pick" c)]
              (if (zero? exit)
                {:branch branch :commit c :status :landed}
                (do (vcs-shell root "git" "cherry-pick" "--abort")
                    {:branch branch :commit c :status :conflict :error err}))))))
       :delete (fn [branch] (vcs-shell root "git" "branch" "-D" branch) nil)}
      :jj
      {:vcs     :jj
       :working (fn [] "@")
       :branches (fn [prefix] (jj-bookmarks root prefix))
       :commits  (fn [_working branch]
                   (->> (:out (vcs-shell root "jj" "log" "--no-graph" "--reversed"
                                       "-r" (str branch " ~ ::@")
                                       "-T" "commit_id ++ \"\n\""))
                        str/split-lines (map str/trim) (remove str/blank?) vec))
       :land-branch
       (fn [_working branch commits]
         ;; Rebase the bookmark's unique commit roots onto @ in one move,
         ;; preserving internal order; conflicts materialize in the working
         ;; change, so undo and report rather than guess.
         (let [{:keys [exit err]}
               (if (seq commits)
                 (vcs-shell root "jj" "rebase" "-s"
                            (str "roots(" branch " ~ ::@)") "-d" "@")
                 {:exit 0 :out "" :err ""})]
           (if (zero? exit)
             (vec (for [c commits] {:branch branch :commit c :status :landed}))
             (do (vcs-shell root "jj" "undo")
                 [{:branch branch :commit (first commits)
                   :status :conflict :error err}]))))
       :delete (fn [branch] (vcs-shell root "jj" "bookmark" "delete" branch) nil)}
      nil)))
