#!/usr/bin/env bb
;; Policy: gate land operations on a green lane run.
;;
;; One source for both runtimes. Reads hook JSON on stdin, detects the
;; caller, runs the policy, emits the shape that caller needs:
;;   - Claude Code: {"tool_input" {"command" "..."} "transcript_path" "..."}
;;     on deny emits {"hookSpecificOutput" {"permissionDecision" "deny" ...}},
;;     on allow emits nothing. cwd is the hook's working dir.
;;   - Normalized (the OpenCode plugin): {"command" "...", "cwd" "..."}
;;     emits {"allow" true} or {"allow" false, "reason" "..."}.
;;
;; A command is a land op if it pushes or merges onto the trunk. A green
;; marker is the lanes-green file in the spine working dir (default
;; .agentic-sdk/state, or SPINE_WORK_DIR), or VERDICT: PASS in the
;; transcript. Fail-safe: any parse error allows (nothing emitted).

(require '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def land-patterns
  "Full-string regexes. A command is a land op if any matches it whole."
  [#".*git +push.*"
   #".*jj +git +push.*"
   #".*jj +bookmark +(move|set) +main.*"
   #".*git +merge.*"])

(defn land? [cmd]
  (some #(re-matches % cmd) land-patterns))

(defn green? [{:keys [cwd transcript]}]
  (let [work-dir (or (System/getenv "SPINE_WORK_DIR") ".agentic-sdk/state")
        base     (if (empty? cwd) "." cwd)]
    (or (.isFile (io/file base work-dir "lanes-green"))
        (when (and transcript (.isFile (io/file transcript)))
          (re-find #"VERDICT: PASS" (slurp (io/file transcript)))))))

(def reason
  (str "require-tests-before-land: no green lane marker this session. "
       "Run the pre-land lanes first; the verifier records VERDICT: PASS "
       "and writes the lanes-green marker."))

(defn -main [& _]
  (let [m (try (json/parse-string (slurp *in*) true)
               (catch Exception _ nil))]
    (when (map? m)
      (let [cc?        (map? (:tool_input m))
            command    (or (and cc? (-> m :tool_input :command)) (:command m))
            cwd        (or (:cwd m) (System/getProperty "user.dir"))
            transcript (or (and cc? (:transcript_path m)) (:transcript m))]
        (cond
          (and command (land? command)
               (not (green? {:cwd cwd :transcript transcript})))
          (println (json/encode
                    (if cc?
                      {:hookSpecificOutput
                       {:permissionDecision "deny"
                        :permissionDecisionReason reason}}
                      {:allow false :reason reason})))

          (not cc?)
          (println (json/encode {:allow true})))))))

(-main)
