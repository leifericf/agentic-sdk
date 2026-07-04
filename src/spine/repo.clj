(ns spine.repo
  "The store seam. Abstracts where spine facts are stored and how they
  are queried. Two backings: EDN files (default) and the mino store
  (when :spine :store :mino). Both implement the same API; callers
  never touch files or the store directly.

  The project home is ~/.agentic-sdk/<project-name>/, resolved from the
  canonical cwd basename. The working dir (default state/) holds the
  fact store. All fact read/write goes through this namespace. Text
  projections (rendered markdown) also go through here so the store
  impl can own the projection contract."
  (:require [spine.host :as host]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;;;; project resolution

(defn project-name
  "The project name: basename of the canonical cwd."
  []
  (host/file-name (host/canonicalize ".")))

(defn project-home
  "The per-project state directory: ~/.agentic-sdk/<project-name>/."
  []
  (host/path (System/getenv "HOME")
             ".agentic-sdk"
             (project-name)))

(defn sdk-src
  "The SDK source root from $AGENTIC_SDK_SRC (set by the agentic CLI or
  the user's shell profile). Falls back to ~/Code/agentic-sdk."
  []
  (or (System/getenv "AGENTIC_SDK_SRC")
      (host/path-str (host/path (System/getenv "HOME") "Code" "agentic-sdk"))))

;;;; store detection

(declare working-dir pr-edn norm-path)

(def ^:private store-ns?
  "True when mino.store is loadable. mino auto-loads its own lib/
  directory at startup, where mino.store ships; when the running mino
  lacks it the spine falls back to EDN files."
  (try
    (require 'mino.store)
    (boolean (find-ns 'mino.store))
    (catch _ false)))

(def ^:private -store-conn (atom nil))
(def ^:private -store-root (atom nil))
(def ^:private -store-wd (atom nil))

(defn- store-mode-for
  "Read :spine :store from the descriptor at root. Returns :edn or :mino."
  [root]
  (let [f (host/path root "project.edn")]
    (when (host/exists? f)
      (-> (edn/read-string (slurp (host/path-str f)))
          (get-in [:spine :store] :edn)))))

(defn- ensure-store
  "Open the store for root if not already open. Returns the conn or nil."
  [root]
  (when (and store-ns? (= :mino (store-mode-for root)))
    (when (or (nil? @-store-conn) (not= root @-store-root))
      (let [wd (working-dir root)
            spath (str (host/path-str wd) "/store.db")]
        (host/create-dirs wd)
        (let [open-fn @(resolve 'mino.store/open)]
          (reset! -store-conn (open-fn spath))
          (reset! -store-root root)
          (reset! -store-wd (host/path-str wd)))))
    @-store-conn))

(defn- store-read
  "Read an EDN value from the store by path key."
  [conn path]
  (let [db-fn @(resolve 'mino.store/db)
        find-fn @(resolve 'mino.store/find-by)
        read-fn @(resolve 'mino.store/read)
        db (db-fn conn)
        eid (find-fn db :spine/path path)]
    (when eid
      (edn/read-string (read-fn db eid :spine/value)))))

(defn- store-write!
  "Write an EDN value to the store under path key."
  [conn path value]
  (let [put-fn @(resolve 'mino.store/put)
        npath (norm-path path)
        eid (keyword (str/replace (str/replace npath "/" ".") "." "-"))
        serialized (pr-edn value)]
    (put-fn conn eid :spine/path npath)
    (put-fn conn eid :spine/value serialized)
    value))

(defn- store-read-collection
  "Read all values in a collection from the store."
  [conn dir]
  (let [db-fn @(resolve 'mino.store/db)
        where-fn @(resolve 'mino.store/where)
        db (db-fn conn)
        ndir (norm-path dir)]
    (->> (where-fn db (fn [e]
                        (let [p (:spine/path e)]
                          (and p (str/starts-with? p ndir)))))
         (map (fn [e] (edn/read-string (:spine/value e))))
         (mapcat (fn [data] (if (map? data) [data] data)))
         vec)))

(defn- store-clear-collection!
  "Retract all values in a collection from the store."
  [conn dir]
  (let [db-fn @(resolve 'mino.store/db)
        where-fn @(resolve 'mino.store/where)
        retract-fn @(resolve 'mino.store/retract)
        db (db-fn conn)
        ndir (norm-path dir)]
    (doseq [e (where-fn db (fn [ent]
                             (let [p (:spine/path ent)]
                               (and p (str/starts-with? p ndir)))))]
      (retract-fn conn (:db/id e) :spine/path)
      (retract-fn conn (:db/id e) :spine/value))))

;;;; working dir

(defn init!
  "Initialize the repo for root. Opens the store when :spine :store
  :mino is set in the descriptor. Called by task wrappers at start."
  [root]
  (ensure-store root))

(defn close!
  "Close the store if open and flush pending writes. No-op when using
  EDN files. Called by task wrappers before exit."
  []
  (when @-store-conn
    (let [close-fn @(resolve 'mino.store/close)]
      (close-fn @-store-conn))
    (reset! -store-conn nil)))

;;;; session sealing (Phase 5)

(defn seal!
  "Capture a hermetic session bundle under artifacts/sessions/<run-id>/.
  The bundle contains: a store snapshot (or EDN file copies), the git
  HEAD ref, and a manifest. Returns the session directory path."
  ([root] (seal! root (str (System/currentTimeMillis))))
  ([root run-id]
   (let [wd       (working-dir root)
         sess-dir (host/path root "artifacts" "sessions" run-id)
         git-ref  (let [r (host/shell "git" "rev-parse" "HEAD")]
                    (str/trim (:out r)))]
     (host/create-dirs sess-dir)
     ;; Snapshot the store or EDN files
     (let [store-file (host/path wd "store.db")]
       (when (host/exists? store-file)
         (let [content (slurp (host/path-str store-file))]
           (spit (host/path-str (host/path sess-dir "store.db")) content))))
     ;; Record git ref
     (spit (host/path-str (host/path sess-dir "git-ref.txt")) git-ref)
     ;; Write manifest
     (spit (host/path-str (host/path sess-dir "manifest.edn"))
           (pr-edn {:run-id run-id
                    :git-ref git-ref
                    :sealed-at (System/currentTimeMillis)}))
      (host/path-str sess-dir))))

(defn- norm-path
  "Normalize a path string: remove leading ./ and collapse double slashes."
  [p]
  (let [s (host/path-str p)
        s (if (str/starts-with? s "./") (subs s 2) s)]
    (str/replace s #"//+" "/")))

(defn- store-active?
  "True when the store is open and path is under the working dir."
  [path]
  (and @-store-conn
       @-store-wd
       (let [ps (norm-path path)
             wd (norm-path @-store-wd)]
         (str/starts-with? ps wd))))

(defn working-dir
  "The spine working dir under root (the project home). Honors the project
  descriptor's :spine :working-dir when present, else defaults to state/.
  Created on first write, never assumed to exist on read."
  [root]
  (let [f (host/path root "project.edn")]
    (host/path root
               (if (host/exists? f)
                 (or (get-in (edn/read-string (slurp (host/path-str f)))
                             [:spine :working-dir])
                     "state")
                 "state"))))

;;;; deterministic EDN serialization

(defn pr-edn
  "Serialize value as deterministic EDN. Map keys are sorted by their
  string form. Output is stable: same inputs always produce the same
  bytes. Replaces pprint which varies by runtime hash ordering."
  ([v] (pr-edn v 0))
  ([v indent]
   (let [pad (apply str (repeat indent "  "))
         cpad (apply str (repeat (inc indent) "  "))
         sep (str ",\n")]
     (cond
       (nil? v) "nil"
       (instance? Boolean v) (if v "true" "false")
       (string? v) (pr-str v)
       (keyword? v) (str v)
       (number? v) (pr-str v)
       (symbol? v) (str v)
       (and (map? v) (empty? v)) "{}"
       (map? v)
       (str "{\n"
            (str/join sep
                      (for [[k v2] (sort-by (fn [[k _]] (str k)) v)]
                        (str cpad (pr-edn k 0) " " (pr-edn v2 (inc indent)))))
            "\n" pad "}")
       (and (vector? v) (empty? v)) "[]"
       (vector? v)
       (str "[\n"
            (str/join "\n"
                      (for [item v]
                        (str cpad (pr-edn item (inc indent)))))
            "\n" pad "]")
       (and (set? v) (empty? v)) "#{}"
       (set? v)
       (str "#{\n"
            (str/join "\n"
                      (for [item (sort-by str v)]
                        (str cpad (pr-edn item (inc indent)))))
            "\n" pad "})")
       (seq? v) (str "(" (str/join " " (for [item v] (pr-edn item 0))) ")")
       :else (pr-str v)))))

;;;; EDN fact read/write

(defn read-edn
  "Parse an EDN value at path, nil if it does not exist. Routes to the
  store when active; reads from file otherwise."
  [path]
  (if (store-active? path)
    (store-read @-store-conn (host/path-str path))
    (let [p (host/path path)]
      (when (host/exists? p)
        (edn/read-string (slurp (host/path-str p)))))))

(defn write-edn!
  "Write value as deterministic EDN to path. Routes to the store when
  active; writes to file otherwise."
  [path value]
  (if (store-active? path)
    (store-write! @-store-conn (host/path-str path) value)
    (let [p (host/path path)]
      (host/create-dirs (host/parent p))
      (spit (host/path-str p) (pr-edn value))
      value)))

(defn read-edn-in
  "Read an EDN file located by joining segments under root."
  [root & segments]
  (read-edn (host/path-str (apply host/path root (map str segments)))))

;;;; collection operations (findings)

(defn read-collection
  "Read and parse every value in a collection. Routes to the store when
  active; globs *.edn files otherwise. Returns nil when empty."
  [dir]
  (if (store-active? dir)
    (store-read-collection @-store-conn (host/path-str dir))
    (when (host/exists? dir)
      (mapcat (fn [p]
                (let [data (edn/read-string (slurp (host/path-str p)))]
                  (if (map? data) [data] data)))
              (sort (host/glob dir "*.edn"))))))

(defn clear-collection!
  "Clear every value in a collection. Routes to the store when active;
  deletes *.edn files otherwise."
  [dir]
  (if (store-active? dir)
    (store-clear-collection! @-store-conn (host/path-str dir))
    (doseq [p (host/glob dir "*.edn")] (host/delete p))))

;;;; text projection

(defn write-text!
  "Write content as text to path, creating parent dirs. Used for markdown
  projections (punch-list.md, derived agents) that derive from repo facts."
  [path content]
  (let [p (host/path path)]
    (host/create-dirs (host/parent p))
    (spit (host/path-str p) content)))

;;;; escalation

(defn- read-escalations [esc-path]
  (if (host/exists? esc-path)
    (let [data (edn/read-string (slurp (host/path-str esc-path)))]
      (if (map? data) data {:escalations (vec data)}))
    {:escalations []}))

(defn escalate!
  "Append an entry to <working-dir>/escalation.edn instead of guessing.
  entry is a map describing the collision (e.g. two unequal values for one
  key). Returns the full escalations map. The orchestrator surfaces the
  count; a human resolves each one. Never silently pick."
  [root entry]
  (let [wd   (working-dir root)
        path (host/path wd "escalation.edn")
        cur  (read-escalations path)
        nxt  (update cur :escalations (fnil conj []) entry)]
    (write-edn! (host/path-str path) nxt)))

(defn pending-collisions
  "Count of unresolved escalations on disk (0 when none)."
  [root]
  (count (:escalations (read-escalations (host/path (working-dir root)
                                                    "escalation.edn")))))
