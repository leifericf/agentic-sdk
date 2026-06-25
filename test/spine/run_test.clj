(ns spine.run-test
  (:require [clojure.test :refer [deftest is testing]]
            [spine.run :as run]))

(defn- state
  "Build a run state. stages plus zero or more maps merged on top
  (:round :round-cap :found-new? :active-phase :phases)."
  [stages & opts]
  (merge {:round 0 :round-cap 2 :found-new? false
          :stages stages :phases {} :active-phase nil}
         (apply merge opts)))

(deftest run-first-pending-stage
  (let [stages {:lint :done :review :pending :fix :pending}]
    (is (= :review
           (:stage (run/next-directive (state stages)))))))

(deftest next-round-when-under-cap-and-found-new
  (let [stages {:lint :done :review :done :triage :done :fix :done :verify :done}]
    (is (= {:action :next-round :round 1}
           (run/next-directive
            (state stages {:round 0 :round-cap 2 :found-new? true}))))))

(deftest no-next-round-when-not-found-new
  (let [stages (zipmap run/default-stage-order (repeat :done))]
    (is (= :complete
           (:action (run/next-directive
                     (state stages {:found-new? false})))))))

(deftest next-phase-when-rounds-exhausted
  (let [stages (zipmap run/default-stage-order (repeat :done))
        d (run/next-directive
           (state stages
                  {:round 2 :round-cap 2 :found-new? true}
                  {:active-phase :build-phase
                   :phases {:build-phase :active
                            :verify-phase :pending}}))]
    (is (= :next-phase (:action d)))
    (is (= :verify-phase (:phase d)))))

(deftest stage-order-honors-configured-stages
  (let [stg {:lint :pending :triage :pending}]
    (is (= [:lint :triage] (run/stage-order {:stages stg}))
        "configured stages filter the default order, preserving it")))

(deftest uncapped-continues-while-finding-new
  (let [stages (zipmap run/default-stage-order (repeat :done))]
    (is (= :next-round
           (:action (run/next-directive
                     (state stages {:round 99 :round-cap nil
                                    :found-new? true})))))))

(deftest stage-order-honors-configured-stages
  (let [stg {:lint :pending :triage :pending}]
    (is (= [:lint :triage] (run/stage-order {:stages stg}))
        "configured stages filter the default order, preserving it")))
