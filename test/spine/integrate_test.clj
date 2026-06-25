(ns spine.integrate-test
  (:require [clojure.test :refer [deftest is testing]]
            [spine.integrate :as integrate]))

(deftest landings-are-oldest-first-across-sorted-branches
  (let [plan (integrate/order-landings
              {"spine/fix/z" ["c3" "c4"]
               "spine/fix/a" ["c1" "c2"]})]
    (is (= [{:branch "spine/fix/a" :commit "c1"}
            {:branch "spine/fix/a" :commit "c2"}
            {:branch "spine/fix/z" :commit "c3"}
            {:branch "spine/fix/z" :commit "c4"}]
           plan)
        "branches in sorted order, commits oldest-first within each")))

(deftest empty-when-no-branches
  (is (empty? (integrate/order-landings {})))
  (is (empty? (integrate/order-landings {"spine/fix/a" []}))))

(deftest accepts-vector-of-pairs
  (let [plan (integrate/order-landings [["b" ["x"]] ["a" ["y" "z"]]])]
    (is (= [{:branch "a" :commit "y"}
            {:branch "a" :commit "z"}
            {:branch "b" :commit "x"}] plan)
        "pairs land in given order when passed as a vector")))
