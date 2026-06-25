(ns spine.integrate
  "Land the parallel fix loop. Within one editing level the orchestrator
  dispatches N editors, each in its own worktree on a fix branch, each owning
  a disjoint module. Because the modules are disjoint, the per-branch commits
  are conflict-free; this task lands each branch onto the working branch
  oldest-first and reports what landed. Replaces one-module-per-commit
  serialization. VCS-agnostic via the core adapter (jj rebase or git
  cherry-pick)."
  (:require [clojure.string :as str]
            [spine.core :as core]))

(defn order-landings
  "Pure: given an ordered map, a plain map, or a vec of [branch commits] of
  branch to its oldest-first commit list, return a flat ordered list of
  {:branch :commit} oldest-first across all branches. Branches always land
  in sorted (deterministic) order; commits oldest-first within each. Tested
  without touching the VCS."
  [branch->commits]
  (let [pairs (if (map? branch->commits)
                 (sort-by key branch->commits)
                 (sort-by first branch->commits))]
    (vec
     (for [[branch commits] pairs
           c commits]
       {:branch branch :commit c}))))

(defn integrate!
  "Land every fix branch's commits onto the working branch, oldest-first.
  opts: :prefix (default spine/fix/), :delete-branches? (default true).
  Returns {:vcs :working :landed [...] :conflicts [...]}. On any conflict
  the offending branch is aborted (git) or undone (jj) and reported; nothing
  is guessed."
  [root {:keys [prefix delete-branches?]
         :or {prefix "spine/fix/" delete-branches? true}}]
  (let [adapter  (or (core/vcs-adapter root)
                     (throw (ex-info "no jj or git repo at root"
                                     {:root root})))
        working  ((:working adapter))
        branches ((:branches adapter) prefix)
        per-branch (into {} (for [b branches]
                              [b ((:commits adapter) working b)]))
        _ (order-landings per-branch) ;; deterministic order, for record
         results (vec
                  (for [b branches]
                    ((:land-branch adapter) working b (get per-branch b))))
         ;; flatten and bucket
         flat (apply concat results)]
    (when delete-branches?
      (doseq [b branches] ((:delete adapter) b)))
    {:vcs       (:vcs adapter)
     :working   working
     :landed    (filterv #(= :landed (:status %)) flat)
     :conflicts (filterv #(= :conflict (:status %)) flat)}))

(defn -main
  "bb integrate [ROOT] - land the fix branches; exit 0 clean, 1 conflicts."
  [& args]
  (let [root (or (first args) ".")
        {:keys [landed conflicts]} (integrate! root {})]
    (println (format "integrate: %d landed, %d conflicts"
                     (count landed) (count conflicts)))
    (System/exit (if (seq conflicts) 1 0))))
