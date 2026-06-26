#!/usr/bin/env bb
;; PreToolUse hook (Claude Code): deny reads and writes of secret-bearing
;; files. Matcher: Read|Edit|Write. Denies when the basename matches a
;; secret pattern; allows otherwise. Fail-soft: a bad parse allows.

(require '[cheshire.core :as json]
         '[clojure.string :as str])

(defn basename [path]
  (if (str/includes? path "/")
    (subs path (inc (str/last-index-of path "/")))
    path))

(defn secret? [b]
  (or (= b ".env") (str/starts-with? b ".env.")
      (str/ends-with? b ".pem") (str/ends-with? b ".key")
      (= b "id_rsa") (str/starts-with? b "id_rsa.")
      (= b "credentials") (str/starts-with? b "credentials.")
      (= b "secrets") (str/starts-with? b "secrets.")))

(def reason
  "deny-secrets: this basename matches a secret-bearing file. Use a secrets manager, not a tracked file.")

(defn -main [& _]
  (let [m (try (json/parse-string (slurp *in*) true)
               (catch Exception _ nil))]
    (when-let [path (and (map? m) (-> m :tool_input :file_path))]
      (when (secret? (basename path))
        (println (json/encode
                  {:hookSpecificOutput
                   {:permissionDecision "deny"
                    :permissionDecisionReason reason}}))))))

(-main)
