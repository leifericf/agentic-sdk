(ns spine.triage-test
  (:require [clojure.test :refer [deftest is testing]]
            [spine.triage :as triage]))

(deftest dedup-merges-reporters-and-keeps-most-severe
  (let [raw [{:dimension :correctness :severity :low :file "a.c"
              :evidence "nil deref" :rule "nil-check" :reporter "r1"}
             {:dimension :correctness :severity :high :file "a.c"
              :evidence "nil deref" :rule "nil-check" :reporter "r2"}]
        {:keys [findings]} (triage/triage raw)]
    (is (= 1 (count findings)) "same file+evidence+rule collapses to one")
    (is (= :high (:severity (first findings))) "most severe wins")
    (is (= ["r1" "r2"] (:reporters (first findings))) "reporters union, order-independent")))

(deftest uppercase-severity-from-lint-normalizes
  (let [raw [{:dimension :lint :severity :MINOR :file "a.md"
              :evidence "tell" :rule "r"}]
        {:keys [findings]} (triage/triage raw)]
    (is (= :low (:severity (first findings))) "lint uppercase :MINOR normalizes to :low")))

(deftest ordering-is-tier-then-severity-then-file
  (let [raw [{:dimension :lint :severity :low :file "z.md"
              :evidence "lint tell" :rule "r1"}
             {:dimension :style :severity :high :file "z.md"
              :evidence "style issue" :rule "r2"}
             {:dimension :correctness :severity :low :file "a.c"
              :evidence "nil bug" :rule "r3"}
             {:dimension :security :severity :high :file "a.c"
              :evidence "overflow" :rule "r4"}]
        {:keys [findings]} (triage/triage raw)
        levels (map :level findings)
        files (map :file findings)]
    (is (= [:correctness :correctness :style :lint] levels)
        "tier dominates: correctness findings first, then style, then lint")
    (is (= ["a.c" "a.c" "z.md" "z.md"] files)
        "within a tier, severity then file")))

(deftest reporter-level-is-honored-over-dimension-fallback
  (let [raw [{:dimension :style :level :correctness :severity :high
              :file "a.c" :evidence "load-bearing style bug" :rule "r1"}
             {:dimension :security :level :factoring :severity :high
              :file "a.c" :evidence "capability gap" :rule "r2"}]
        levels (map :level (:findings (triage/triage raw)))]
    (is (= [:correctness :factoring] levels)
        "the reporter's :level wins; dimension does not override it")))

(deftest rule-less-opinions-become-queries
  (let [raw [{:dimension :style :severity :low :file "a.c"
              :evidence "feels off" :reporter "r1"}
             {:dimension :correctness :severity :low :file "a.c"
              :evidence "real bug" :rule "nil-check" :reporter "r1"}]
        {:keys [findings queries]} (triage/triage raw)]
    (is (= 1 (count findings)) "the ruled finding is kept")
    (is (= 1 (count queries)) "the rule-less opinion becomes a query")
    (is (:question (first queries)))))

(deftest protected-idioms-vanish
  (let [raw [{:dimension :style :severity :low :file "a.c"
              :evidence "intentionally ugly" :rule "r"}]]
    (is (empty? (:findings (triage/triage raw :protected-idioms ["intentionally ugly"]))))))

(deftest renumbering-is-stable
  (let [raw (for [i (range 3)]
              {:dimension :lint :severity :low :file (str i ".md")
               :evidence "e" :rule "r"})
        ids (map :id (:findings (triage/triage raw)))]
    (is (= ["FINDING-1" "FINDING-2" "FINDING-3"] ids))))
