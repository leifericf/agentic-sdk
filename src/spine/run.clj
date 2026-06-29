(ns spine.run
  "The resumption layer. run.edn is a minimal checkpoint, not a state
  engine: :scope, :round, :round-cap, :found-new?, the :stages map (per
  stage in the current round), the :phases map (per phase in the campaign),
  plus the hashes of the inputs that re-arm gates. The orchestrator stays
  near-stateless: after each phase or round it advances run.edn and reads
  `bb run status` to learn the next directive. Because the directive is
  computed from disk, a killed run resumes from disk."
  (:require [spine.host :as host]
            [spine.repo :as repo]
            [clojure.edn :as edn]))

(def default-stage-order
  "The per-round review loop for software: mechanical lint, reviewer fan-out,
  fold findings, editor waves, then verify lanes. Configurable via init opts
  or the descriptor."
  [:lint :review :triage :fix :verify])

;; --- hashing (arm the gates) ---------------------------------------------

(defn- file-hash [root name]
  (let [f (host/path root name)]
    (when (host/exists? f) (host/sha256 (slurp (host/path-str f))))))

;; --- state read/write ----------------------------------------------------

(defn- run-file [root] (host/path (repo/working-dir root) "run.edn"))

(defn read-state [root]
  (repo/read-edn (run-file root)))

(defn write-state! [root state]
  (repo/write-edn! (run-file root) state))

;; --- descriptor / plan discovery ----------------------------------------

(defn- read-descriptor [root]
  (or (repo/read-edn (host/path root "project.edn"))
      {}))

(defn- read-plan [root]
  ;; A plan names the campaign's phases. The active campaign lives at
  ;; runs/current/plan.edn under the project home; absence means a single
  ;; implicit phase.
  (or (repo/read-edn (host/path root "runs" "current" "plan.edn"))
      {}))

(defn- plan-phases [plan]
  (mapv #(if (map? %) (:id %) %) (:phases plan)))

(defn- plan-scope [plan]
  (:scope plan))

;; --- init / advance / status --------------------------------------------

(defn init!
  "Seed run.edn from the plan and descriptor. opts override: :round-cap
  (default 2; audit-code passes a higher cap), :stages (ordered stage
  keywords), :phases (explicit phase ids), :scope."
  [root {:keys [round-cap stages phases scope]
         :or {round-cap 2}}]
  (let [plan   (read-plan root)
        desc   (read-descriptor root)
        cap    (or round-cap (get-in desc [:spine :round-cap]) 2)
        order  (or stages (get-in desc [:stages]) default-stage-order)
        phs    (or phases (plan-phases plan) [:build])
        state {:scope       (or scope (plan-scope plan) {})
               :round       0
               :round-cap   cap
               :found-new?  false
               :active-phase (first phs)
               :stages      (zipmap order (repeat :pending))
               :phases      (-> (zipmap phs (repeat :pending))
                                (assoc (first phs) :active))
                 :plan-hash     (file-hash root "runs/current/plan.edn")
                 :descriptor-hash (file-hash root "project.edn")}]
    (write-state! root state)))

(defn advance!
  "Deep-merge updates into run.edn. Maps merge one level (so {:stages
  {:lint :done}} updates just that stage); other values replace. The
  orchestrator sends round resets and phase transitions as updates."
  [root updates]
  (let [state  (or (read-state root) {})
        merged (reduce-kv (fn [m k v]
                            (if (and (map? v) (map? (get m k)))
                              (assoc m k (merge (get m k) v))
                              (assoc m k v)))
                          state updates)]
    (write-state! root merged)))

(defn stage-order
  "The ordered stages for the current state, falling back to the default."
  [state]
  (let [ks (keys (:stages state))]
    (if (seq ks) (filter (set ks) default-stage-order) default-stage-order)))

(defn next-directive
  "The single next action, computed purely from the state: run the first
  pending stage in the active round, start the next round when under cap and
  new findings appeared, advance to the next phase, or complete."
  [state]
  (let [stages (:stages state)
        order  (stage-order state)
        pending-stage (first (filter #(not= :done (get stages %)) order))
        round  (:round state)
        cap    (:round-cap state)
        under-cap? (or (nil? cap) (< round cap))]
    (cond
      pending-stage {:action :run-stage :stage pending-stage :round round}
      (and under-cap? (:found-new? state)) {:action :next-round
                                            :round (inc round)}
      :else (let [phases (:phases state)
                  nxt (first (for [[p s] (seq phases)
                                   :when (or (= s :pending) (= s :active))
                                   :when (not= p (:active-phase state))]
                               p))]
              (if nxt
                {:action :next-phase :phase nxt}
                {:action :complete})))) )

(defn- stale-phases [state]
  (for [[p s] (:phases state) :when (= s :pending)] p))

(defn status
  "The orchestrator's resumption read: the next directive plus the bounded
  signals it needs (pending collisions, pending phases, whether the
  gate-arming inputs changed since init)."
  [root]
  (let [state (or (read-state root) {})]
    {:directive          (next-directive state)
     :round              (:round state)
     :round-cap          (:round-cap state)
     :active-phase       (:active-phase state)
     :collisions-pending (repo/pending-collisions root)
     :pending-phases     (vec (stale-phases state))
       :plan-changed?      (not= (:plan-hash state)
                                 (file-hash root "runs/current/plan.edn"))
       :descriptor-changed? (not= (:descriptor-hash state)
                                  (file-hash root "project.edn"))}))

(defn -main
  "bb run init|status|advance [ROOT] [EDN-OPTS]. status prints the directive
  map and exits 0 only when complete; init seeds run.edn; advance merges an
  EDN updates map."
  [& args]
  (let [[cmd root-arg edn-arg] args
        root (or root-arg ".")]
    (case cmd
      "init" (do (init! root (if edn-arg (edn/read-string edn-arg) {}))
                 (println "run: initialized"))
      "advance" (do (advance! root (edn/read-string (or edn-arg "{}")))
                    (println "run: advanced"))
      "status" (let [s (status root)]
                 (println (pr-str s))
                 (System/exit (if (= :complete (:action (:directive s))) 0 1)))
      (do (binding [*out* *err*]
            (println "usage: bb run init|status|advance [ROOT] [EDN]"))
          (System/exit 2)))))
