(ns spine.triage-test
  (:require [clojure.test :refer [deftest is testing]]
            [spine.triage :as triage]))

(deftest dedup-merges-reporters-and-keeps-most-severe
  (let [raw [{:dimension :correctness :severity :MINOR :file "a.c"
              :evidence "nil deref" :rule "nil-check" :reporter "r1"}
             {:dimension :correctness :severity :CRITICAL :file "a.c"
              :evidence "nil deref" :rule "nil-check" :reporter "r2"}]
        {:keys [findings]} (triage/triage raw)]
    (is (= 1 (count findings)) "same file+evidence+rule collapses to one")
    (is (= :CRITICAL (:severity (first findings))) "most severe wins")
    (is (= ["r1" "r2"] (:reporters (first findings))) "reporters union, order-independent")))

(deftest ordering-is-level-then-severity-then-file
  (let [raw [{:dimension :lint :severity :MINOR :file "z.md"
              :evidence "lint tell" :rule "r1"}
             {:dimension :style :severity :CRITICAL :file "z.md"
              :evidence "style issue" :rule "r2"}
             {:dimension :correctness :severity :MINOR :file "a.c"
              :evidence "nil bug" :rule "r3"}
             {:dimension :security :severity :SIGNIFICANT :file "a.c"
              :evidence "overflow" :rule "r4"}]
        {:keys [findings]} (triage/triage raw)
        levels (map :level findings)
        files (map :file findings)]
    (is (= [1 1 3 4] levels) "level dominates: security, correctness, style, lint")
    (is (= ["a.c" "a.c" "z.md" "z.md"] files)
        "within a level, severity then file")))

(deftest rule-less-opinions-become-queries
  (let [raw [{:dimension :style :severity :MINOR :file "a.c"
              :evidence "feels off" :reporter "r1"} ;; no :rule
             {:dimension :correctness :severity :MINOR :file "a.c"
              :evidence "real bug" :rule "nil-check" :reporter "r1"}]
        {:keys [findings queries]} (triage/triage raw)]
    (is (= 1 (count findings)) "the ruled finding is kept")
    (is (= 1 (count queries)) "the rule-less opinion becomes a query")
    (is (:question (first queries)))))

(deftest protected-idioms-vanish
  (let [raw [{:dimension :style :severity :MINOR :file "a.c"
              :evidence "intentionally ugly" :rule "r"}]]
    (is (empty? (:findings (triage/triage raw :protected-idioms ["intentionally ugly"]))))))

(deftest renumbering-is-stable
  (let [raw (for [i (range 3)]
              {:dimension :lint :severity :MINOR :file (str i ".md")
               :evidence "e" :rule "r"})
        ids (map :id (:findings (triage/triage raw)))]
    (is (= ["FINDING-1" "FINDING-2" "FINDING-3"] ids))))
