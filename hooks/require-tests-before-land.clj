#!/usr/bin/env mino
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
;; marker is the lanes-green file in the spine working dir, or VERDICT: PASS
;; in the transcript. Fail-safe: any parse error allows (nothing emitted).

(let [sdk (or (System/getenv "AGENTIC_SDK_SRC")
              (str (System/getenv "HOME") "/Code/agentic-sdk"))]
  (add-load-path! (str sdk "/src")))

(require '[spine.host :as host]
         '[spine.repo :as repo])

(def land-patterns
  "Full-string regexes. A command is a land op if any matches it whole."
  [#".*git +push.*"
   #".*jj +git +push.*"
   #".*jj +bookmark +(move|set) +main.*"
   #".*git +merge.*"])

(defn land? [cmd]
  (some #(re-matches % cmd) land-patterns))

(defn green? [{:keys [cwd transcript]}]
  (let [home (repo/project-home)
        wd (repo/working-dir home)]
    (or (host/exists? (host/path wd "lanes-green"))
        (when (and transcript (host/exists? transcript))
          (re-find #"VERDICT: PASS" (slurp (host/path-str transcript)))))))

(def reason
  (str "require-tests-before-land: no green lane marker this session. "
       "Run the pre-land lanes first; the verifier records VERDICT: PASS "
       "and writes the lanes-green marker."))

(defn -main [& _]
  (let [m (try (host/json-parse (host/slurp-stdin))
               (catch e nil))]
    (when (map? m)
      (let [cc?        (map? (:tool_input m))
            command    (or (and cc? (-> m :tool_input :command)) (:command m))
            cwd        (or (:cwd m) ".")
            transcript (or (and cc? (:transcript_path m)) (:transcript m))]
        (cond
          (and command (land? command)
               (not (green? {:cwd cwd :transcript transcript})))
          (println (host/json-encode
                    (if cc?
                      {:hookSpecificOutput
                       {:permissionDecision "deny"
                        :permissionDecisionReason reason}}
                      {:allow false :reason reason})))

          (not cc?)
          (println (host/json-encode {:allow true})))))))

(-main)
