(ns spine.core
  "Shared spine helpers: the canonical finding shape and the jj-or-git
  VCS adapter (prefer jj). Software-only; no prose-production logic.
  Runs under mino. Fact storage lives in spine.repo; host access
  lives in spine.host."
  (:require [spine.host :as host]
            [clojure.string :as str]))

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

;; --- VCS adapter: prefer jj, fall back to git ----------------------------

(defn jj-repo? [root] (host/exists? (host/path root ".jj")))
(defn git-repo? [root] (host/exists? (host/path root ".git")))

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
        (apply host/shell {:dir (host/path-str root)} cmd)]
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
