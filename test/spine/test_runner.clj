(ns spine.test-runner
  "The mino task test entry point. Requires every *_test namespace under
  test/spine and runs the suite via clojure.test. Exits non-zero on any
  failure or error."
  (:require [clojure.test :as t]
            [clojure.string :as str]
            [spine.host :as host]))

(defn- file->ns-sym
  "Turn test/spine/foo_test.clj into the symbol spine.foo-test."
  [path]
  (-> (host/path-str path)
      (str/replace "test/" "")
      (str/replace ".clj" "")
      (str/replace "/" ".")
      symbol))

(defn run
  "Require every *_test.clj directly under test/spine and run the suite.
  Prints the count line and exits non-zero when anything fails or errors."
  []
  (doseq [f (host/glob "test/spine" "*.clj")
          :when (str/ends-with? (host/path-str f) "_test.clj")]
    (require (file->ns-sym f)))
  (let [{:keys [fail error]} (t/run-all-tests (re-pattern "spine\\..*-test"))
        total (+ fail error)]
    (println (format "test: %d fail, %d error" fail error))
    (when (pos? total) (System/exit 1))))
