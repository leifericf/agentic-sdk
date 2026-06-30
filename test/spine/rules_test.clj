(ns spine.rules-test
  (:require [clojure.test :refer [deftest is testing]]
            [spine.rules :as rules]
            [spine.host :as host]))

(def decisions
  {:banned-categories [{:id :em-dash :pattern "\u2014"
                        :message "Em-dash banned" :level :warning}
                       "LegacyFoo"]
   :naming {:fn "[a-z][a-z0-9-]*"}
   :commit-categories ["Build" "Tests" "Fix" "Build"]})

(defn- tmp-dir [slug]
  (let [p (host/path "/tmp" (str slug "-" (System/currentTimeMillis)))]
    (host/create-dirs p)
    p))

(deftest projection-is-deterministic
  (let [a (rules/project decisions)
        b (rules/project (assoc decisions :banned-categories
                                (reverse (:banned-categories decisions))))]
    (is (= a b) "input order does not change the projected output")
    (is (= ["Build" "Fix" "Tests"]
           (-> decisions rules/commit-categories))
        "categories sorted, deduped")))

(deftest bare-term-becomes-a-quoted-pattern
  (let [bps (rules/banned-patterns {:banned-categories ["LegacyFoo"]})
        rule (first bps)]
    (is (= :legacyfoo (:id rule)))
    (is (re-find (re-pattern (:pattern rule)) "use LegacyFoo here"))))

(deftest naming-becomes-a-banned-mismatch-rule
  (let [bps (rules/banned-patterns {:naming {:fn "[A-Z].*"}})]
    (is (= :naming-fn (:id (first bps))))))

(deftest emit-writes-both-files-when-categories-present
  (let [tmp (tmp-dir "rules-test")
        {:keys [files]} (rules/emit! decisions tmp)]
    (is (some #{"lint-rules.edn"} files))
    (is (some #{"commit-categories.edn"} files))
    (host/delete tmp)))

(deftest emit-skips-categories-when-absent
  (let [tmp (tmp-dir "rules-test2")
        {:keys [files]} (rules/emit! {:banned-categories ["X"]} tmp)]
    (is (= ["lint-rules.edn"] files))
    (host/delete tmp)))
