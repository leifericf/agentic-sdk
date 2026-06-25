#!/usr/bin/env bb
;; Policy: gate land operations on a green lane run this session.
;;
;; Pure policy. Reads a normalized JSON object on stdin and writes a decision
;; JSON object on stdout:
;;   in :  {"command": "...", "cwd": "...", "transcript": "..."}  (all optional)
;;   out:  {"allow": true} | {"allow": false, "reason": "..."}
;;
;; The OpenCode plugin shim and the Claude Code shell hook both adapt their
;; runtime event shape to this normalized input, so the policy lives in one
;; place. A command is a "land" if it pushes or merges onto the trunk. A green
;; marker is the spine working dir's lanes-green file, the per-repo working
;; dir's lanes-green file, or a recorded VERDICT: PASS line in the transcript.

(require '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def land-patterns
  "Full-string regexes. A command is a land op if any matches it whole."
  [#".*git +push.*"
   #".*jj +git +push.*"
   #".*jj +bookmark +(move|set) +main.*"
   #".*git +merge.*"])

(defn land? [cmd]
  (some #(re-matches % cmd) land-patterns))

(defn repo-dir-name [cwd]
  (let [base (if (empty? cwd) "." cwd)
        trimmed (str/replace base #"/+$" "")]
    (str "." (last (str/split trimmed #"/")))))

(defn green? [{:keys [cwd transcript]}]
  (let [work-dir (or (System/getenv "SPINE_WORK_DIR") ".spine")
        base (if (empty? cwd) "." cwd)
        repo-dir (repo-dir-name cwd)]
    (or (.isFile (io/file base work-dir "lanes-green"))
        (.isFile (io/file base repo-dir "lanes-green"))
        (when (and transcript (.isFile (io/file transcript)))
          (let [text (slurp (io/file transcript))]
            (boolean (re-find #"VERDICT: PASS" text)))))))

(defn -main [& _]
  (let [in (try (json/parse-string (slurp *in*) true)
                (catch Exception _ {}))
        cmd (or (:command in) "")]
    (cond
      (or (empty? cmd) (not (land? cmd)))
      (println (json/encode {:allow true}))

      (green? in)
      (println (json/encode {:allow true}))

      :else
      (println (json/encode
                {:allow false
                 :reason
                 (str "require-tests-before-land: no green lane marker this "
                      "session. Run the pre-land lanes first; the verifier "
                      "records VERDICT: PASS and writes the lanes-green marker.")})))))

(-main)
