(ns spine.host
  "The runtime seam. Abstracts host capabilities (filesystem paths,
  process execution, JSON, hashing) so the seven spine namespaces and
  three hooks depend only on this surface.

  Runs under mino. Host primitives (realpath, which, sha256, run,
  file-exists?, mkdir-p, rm-rf, file-seq) are mino core symbols,
  captured once at load time via resolve. The task namespaces call
  host/path, host/exists?, host/shell, and so on; they never touch a
  runtime namespace directly."
  (:require [clojure.string :as str]
            [spine.json :as json]))

;; Capture mino primitives once at load.
(def ^:private fs
  {:file-exists? @(resolve 'file-exists?)
   :mkdir-p      @(resolve 'mkdir-p)
   :rm-rf        @(resolve 'rm-rf)
   :file-seq     @(resolve 'file-seq)
   :realpath     @(resolve 'realpath)
   :which        @(resolve 'which)
   :sha256       @(resolve 'sha256)
   :run          @(resolve 'run)})

;; --- path construction and inspection -----------------------------------

(defn path
  "Build a path string from segments, dropping nils."
  [& segments]
  (->> (filter identity segments) (interpose "/") (apply str)))

(defn path-str
  "The string form of a path object or string."
  [p]
  (str p))

(defn parent
  "The parent path of p, or nil when p has no parent."
  [p]
  (let [s (str p) idx (str/last-index-of s "/")]
    (if idx (subs s 0 idx) nil)))

(defn file-name
  "The basename of p as a string."
  [p]
  (let [s (str p) idx (str/last-index-of s "/")]
    (if idx (subs s (inc idx)) s)))

(defn components
  "The path components of p as a seq of strings."
  [p]
  (str/split (str p) #"/"))

(defn extension
  "The file extension of p (without the dot), or nil."
  [p]
  (let [s (file-name p) idx (str/last-index-of s ".")]
    (if (and idx (> idx 0)) (subs s (inc idx)) nil)))

(defn canonicalize
  "Resolve p to its canonical (absolute, symlink-free) path. Falls
  back to p when realpath cannot resolve it."
  [p]
  (let [r ((:realpath fs) (str p))]
    (if (nil? r) p r)))

;; --- filesystem predicates and operations -------------------------------

(defn exists?
  "True when p (a path or string) exists on the filesystem."
  [p]
  ((:file-exists? fs) (str p)))

(defn- glob-flat
  "Files directly under dir-s whose name ends with suffix."
  [dir-s suffix]
  (let [dlen (count dir-s)]
    (->> ((:file-seq fs) dir-s)
         (filter (fn [path]
                   (and (str/ends-with? path suffix)
                        (let [rel (subs path (min dlen (count path)))]
                          (not (str/includes? (subs rel 1) "/"))))))
         sort)))

(defn- glob-immediate-children
  "List immediate children (files and dirs) of dir-s. file-seq returns
  files only, so directories are inferred from path segments."
  [dir-s]
  (let [dlen (count dir-s)
        prefix (if (str/ends-with? dir-s "/") dir-s (str dir-s "/"))
        plen (count prefix)]
    (->> ((:file-seq fs) dir-s)
         (map (fn [path]
                (let [rel (subs path (min plen (count path)))
                      slash (str/index-of rel "/")]
                  (if slash
                    (str prefix (subs rel 0 slash))
                    path))))
         distinct
         sort)))

(defn glob
  "Seq of paths under dir matching the glob pattern. Supports **<suffix>
  (recursive suffix match), *.ext (flat in one dir), * (immediate
  children), and a regex over the basename."
  [dir pattern]
  (let [dir-s (str dir)]
    (when ((:file-exists? fs) dir-s)
      (cond
        (str/starts-with? pattern "**")
        (->> ((:file-seq fs) dir-s)
             (filter #(str/ends-with? % (subs pattern 2)))
             sort)
        (str/starts-with? pattern "*.")
        (glob-flat dir-s (subs pattern 1))
        (= pattern "*")
        (glob-immediate-children dir-s)
        :else
        (->> ((:file-seq fs) dir-s)
             (filter #(re-find (re-pattern pattern) (file-name %)))
             sort)))))

(defn create-dirs
  "Create the directory p and all missing parents. Idempotent."
  [p]
  (do ((:mkdir-p fs) (str p)) p))

(defn delete
  "Delete the file at p."
  [p]
  (do ((:rm-rf fs) (str p)) nil))

(defn which
  "The absolute path to cmd on PATH, or nil when not found."
  [cmd]
  ((:which fs) cmd))

;; --- process execution ---------------------------------------------------

(defn shell
  "Run cmd (with optional args) under opts, returning {:out :err :exit}.
  Never throws on non-zero exit. When the first argument is a map it
  is taken as opts (e.g. {:dir root}); otherwise opts is empty."
  [cmd & args]
  (let [opts      (if (map? cmd) cmd {})
        real-cmd  (if (map? cmd) (first args) cmd)
        real-args (if (map? cmd) (rest args) args)]
    (apply (:run fs) opts real-cmd real-args)))

;; --- JSON (hooks) --------------------------------------------------------

(defn json-parse
  "Parse JSON string s to Clojure data. Keywordizes keys when keywordize?
  is true (the default)."
  ([s]
   (json-parse s true))
  ([s keywordize?]
   (json/parse s keywordize?)))

(defn json-encode
  "Encode data as a JSON string."
  [data]
  (json/emit data))

;; --- stdin (hooks) -------------------------------------------------------

(defn slurp-stdin
  "Read all of stdin as a string. Uses read-line so it works under mino
  (which lacks *in* as a Reader)."
  []
  (letfn [(step [lines]
            (let [line (read-line)]
              (if (nil? line)
                (apply str (interpose "\n" lines))
                (recur (conj lines line)))))]
    (step [])))

;; --- hashing -------------------------------------------------------------

(defn sha256
  "The sha256 of s prefixed with sha256:. Used for gate-arming hashes."
  [s]
  (str "sha256:" ((:sha256 fs) s)))
