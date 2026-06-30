(ns spine.opencode
  "Project .claude/agents/*.md (the masters) into .opencode/agent/*.md (the
  derived OpenCode format). One source of truth, two runtimes; the derived
  files are never hand-edited. Proven in prior deployments. See the
  runtime-port section of the design doc.

  Per master: copy name, description, and the prompt body verbatim; map the
  Claude tools allowlist to an OpenCode permission block (edit from Edit or
  Write, bash from Bash, task from Agent); inject mode: subagent; drop
  model: inherit so OpenCode uses the session model; map model: haiku to the
  OPENCODE_SMALL_MODEL env var, omitting the field when that var is unset.
  The masters live at $SDK_SRC/agents/ (the SDK source)."
  (:require [spine.host :as host]
            [spine.repo :as repo]
            [clojure.string :as str]))

(def ^:private stamp
  "# AUTO-GENERATED from the master agent by `agentic agents sync`. Do not edit;
  edit the master and re-run.")

(defn- masters-dir
  "The agent masters dir at $SDK_SRC/agents/. Resolves the SDK source
  from $AGENTIC_SDK_SRC or the repo/sdk-src derivation. Canonicalizes
  so glob descends properly."
  [root]
  (let [d (host/path (repo/sdk-src) "agents")]
    (when (host/exists? d)
      (host/path-str (host/canonicalize d)))))

(defn- derived-dir [root] (host/path root ".opencode" "agent"))

;; --- frontmatter parse/emit ----------------------------------------------

(defn- split-frontmatter
  "Return {:frontmatter str :body str} or nil when the file has no YAML
  fence."
  [text]
  (when (str/starts-with? text "---\n")
    (let [rest (subs text 4)
          end (str/index-of rest "\n---")]
      (when end
        {:frontmatter (subs rest 0 end)
         :body        (subs rest (+ end 4))}))))

(defn- parse-yaml-ish
  "A deliberately small YAML reader for the flat, known agent frontmatter:
  key: value lines and one nested block (skills is a list; ignored here).
  Returns a map keyworded."
  [^String s]
  (->> (str/split-lines s)
       (remove str/blank?)
       (reduce (fn [m line]
                 (if-let [[_ k v] (re-matches #"^([A-Za-z0-9_-]+):\s*(.*)$" line)]
                   (assoc m (keyword k) (str/trim v)) m))
               {})))

(defn- tool->permission
  "Map a Claude tools string to OpenCode permission entries. Coarse on
  purpose: edit from Edit or Write, bash from Bash, task from
  Agent. Read/Grep/Glob need no entry."
  [tools]
  (let [toks (set (map str/trim (str/split (or tools "") #",")))]
    (cond-> {}
      (or (toks "Edit") (toks "Write")) (assoc :edit "allow")
      (toks "Bash")                     (assoc :bash "allow")
      (or (toks "Agent") (toks "Task")) (assoc :task "allow"))))

(defn- model-for
  "Drop :inherit (use session model); map :haiku to OPENCODE_SMALL_MODEL
  when set, else omit; any other alias omitted (tier is a no-op under one
  model)."
  [model]
  (let [m (some-> model str/trim str/lower-case)]
    (cond
      (str/blank? m) nil
      (= m "inherit") nil
      (= m "haiku") (System/getenv "OPENCODE_SMALL_MODEL")
      :else nil)))

(declare render-permission)

(defn derive
  "Pure: master file text -> derived file text. name-from-filename is the
  agent id when the master frontmatter omits name."
  [master-text name-from-filename]
  (let [{:keys [frontmatter body]}
        (or (split-frontmatter master-text)
            {:frontmatter "" :body master-text})
        fm (parse-yaml-ish frontmatter)
        name (or (:name fm) name-from-filename)
        perm (tool->permission (:tools fm))
        model (model-for (:model fm))
        lines (cond-> [(str "name: " name)
                       (str "description: " (:description fm))
                       "mode: subagent"]
                (seq perm) (into (render-permission perm))
                model      (conj (str "model: " model)))]
    (str "---\n" stamp "\n" (str/join "\n" lines) "\n---\n" body)))

(defn- render-permission [perm]
  (into ["permission:"]
        (for [[k v] (sort-by key perm)]
          (str "  " (name k) ": " v))))

;; --- sync / check --------------------------------------------------------

(defn- master-files [root]
  (when-let [d (masters-dir root)]
    (sort (host/glob d "*.md"))))

(defn sync!
  "Project every master into .opencode/agent/. Writes derived files,
  creating the dir. Returns {:wrote [names...]}."
  [root]
  (let [out (derived-dir root)
        wrote (for [p (master-files root)
                    :let [nm (str/replace (host/file-name p) #"\.md$" "")
                          derived (derive (slurp (host/path-str p)) nm)]]
                (do (host/create-dirs out)
                    (spit (host/path-str (host/path out (str nm ".md"))) derived)
                    nm))]
    {:wrote (vec wrote)}))

(defn stale
  "Return the names of derived files that are missing or differ from a fresh
  projection of their master. Empty seq means everything is in sync."
  [root]
  (for [p (master-files root)
        :let [nm (str/replace (host/file-name p) #"\.md$" "")
              target (host/path (derived-dir root) (str nm ".md"))
              want (derive (slurp (host/path-str p)) nm)]
        :when (or (not (host/exists? target))
                  (not= (slurp (host/path-str target)) want))]
    nm))

(defn check
  "Report staleness; returns {:stale [names...] :ok? bool}."
  [root]
  (let [s (vec (stale root))]
    {:stale s :ok? (empty? s)}))

(defn -main
  "agentic agents sync [ROOT] - writes derived agents.
  agentic agents check [ROOT] - exits non-zero with the stale list."
  [cmd & args]
  (let [root (or (first args) ".")]
    (case cmd
      "sync"
      (let [{:keys [wrote]} (sync! root)]
        (println (str "opencode-sync: wrote " (count wrote) " agents"))
        (doseq [n wrote] (println "  " n)))
      "check"
      (let [{:keys [stale ok?]} (check root)]
        (if ok?
          (println "opencode-check: all derived agents in sync")
          (do (binding [*out* *err*]
                (println "opencode-check: stale derived agents:")
                (doseq [n stale] (println "  " n)))
              (System/exit 1))))
      (do (binding [*out* *err*]
            (println "usage: agentic agents sync|check [ROOT]"))
          (System/exit 2)))))
