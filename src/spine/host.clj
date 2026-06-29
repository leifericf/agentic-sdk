(ns spine.host
  "The runtime seam. Abstracts host capabilities (filesystem paths,
  process execution, JSON, hashing) so the seven spine namespaces and
  three hooks depend only on this surface.

  A single source runs under both Babashka and mino. Runtime is
  detected once at load time via find-ns. Babashka functions are
  captured via ns-resolve (quoted symbols avoid analysis-time
  resolution failures). mino primitives are captured the same way."
  (:require [clojure.string :as str]
            [spine.json :as json]))

;; --- runtime detection (no try/catch; find-ns is universal) -------------

(def ^:private ^:const babashka?
  (boolean (find-ns 'babashka.fs)))

(def ^:private ^:const has-json?
  (boolean (find-ns 'cheshire.core)))

;; Capture runtime-specific functions via ns-resolve + deref.
;; Quoted symbols pass both analyzers without resolution failures.
(def ^:private bb-fs
  (when babashka?
    {:path         @(ns-resolve 'babashka.fs 'path)
     :parent       @(ns-resolve 'babashka.fs 'parent)
     :file-name    @(ns-resolve 'babashka.fs 'file-name)
     :components   @(ns-resolve 'babashka.fs 'components)
     :extension    @(ns-resolve 'babashka.fs 'extension)
     :canonicalize @(ns-resolve 'babashka.fs 'canonicalize)
     :exists?      @(ns-resolve 'babashka.fs 'exists?)
     :glob         @(ns-resolve 'babashka.fs 'glob)
     :create-dirs  @(ns-resolve 'babashka.fs 'create-dirs)
     :delete       @(ns-resolve 'babashka.fs 'delete)
     :which        @(ns-resolve 'babashka.fs 'which)}))

(def ^:private bb-proc
  (when babashka?
    @(ns-resolve 'babashka.process 'shell)))

(def ^:private mn-fs
  (when-not babashka?
    {:file-exists? @(resolve 'file-exists?)
     :mkdir-p      @(resolve 'mkdir-p)
     :rm-rf        @(resolve 'rm-rf)
     :file-seq     @(resolve 'file-seq)
     :realpath     @(resolve 'realpath)
     :which        @(resolve 'which)
     :sha256       @(resolve 'sha256)
     :run          @(resolve 'run)}))

;; --- path construction and inspection -----------------------------------

(defn path
  "Build a path from segments. Returns a Path (Babashka) or string (mino)."
  [& segments]
  (if babashka?
    (apply (:path bb-fs) segments)
    (->> (filter identity segments) (interpose "/") (apply str))))

(defn path-str
  "The string form of a path object or string."
  [p]
  (str p))

(defn parent
  "The parent path of p, or nil when p has no parent."
  [p]
  (if babashka?
    ((:parent bb-fs) p)
    (let [s (str p) idx (str/last-index-of s "/")]
      (if idx (subs s 0 idx) nil))))

(defn file-name
  "The basename of p as a string."
  [p]
  (if babashka?
    ((:file-name bb-fs) p)
    (let [s (str p) idx (str/last-index-of s "/")]
      (if idx (subs s (inc idx)) s))))

(defn components
  "The path components of p as a seq of strings."
  [p]
  (if babashka?
    ((:components bb-fs) p)
    (str/split (str p) #"/")))

(defn extension
  "The file extension of p (without the dot), or nil."
  [p]
  (if babashka?
    (some-> ((:extension bb-fs) p) str)
    (let [s (file-name p) idx (str/last-index-of s ".")]
      (if (and idx (> idx 0)) (subs s (inc idx)) nil))))

(defn canonicalize
  "Resolve p to its canonical (absolute, symlink-free) path."
  [p]
  (if babashka?
    ((:canonicalize bb-fs) p)
    (let [r ((:realpath mn-fs) (str p))]
      (if (nil? r) p r))))

;; --- filesystem predicates and operations -------------------------------

(defn exists?
  "True when p (a path or string) exists on the filesystem."
  [p]
  (if babashka?
    ((:exists? bb-fs) p)
    ((:file-exists? mn-fs) (str p))))

(defn- glob-flat-mino
  [dir-s suffix]
  (let [dlen (count dir-s)]
    (->> ((:file-seq mn-fs) dir-s)
         (filter (fn [path]
                   (and (str/ends-with? path suffix)
                        (let [rel (subs path (min dlen (count path)))]
                          (not (str/includes? (subs rel 1) "/"))))))
         sort)))

(defn glob
  "Seq of paths under dir matching the glob pattern."
  [dir pattern]
  (if babashka?
    ((:glob bb-fs) dir pattern)
    (let [dir-s (str dir)]
      (when ((:file-exists? mn-fs) dir-s)
        (cond
          (str/starts-with? pattern "**")
          (->> ((:file-seq mn-fs) dir-s)
               (filter #(str/ends-with? % (subs pattern 2)))
               sort)
          (str/starts-with? pattern "*.")
          (glob-flat-mino dir-s (subs pattern 1))
          :else
          (->> ((:file-seq mn-fs) dir-s)
               (filter #(re-find (re-pattern pattern) (file-name %)))
               sort))))))

(defn create-dirs
  "Create the directory p and all missing parents. Idempotent."
  [p]
  (if babashka?
    ((:create-dirs bb-fs) p)
    (do ((:mkdir-p mn-fs) (str p)) p)))

(defn delete
  "Delete the file at p."
  [p]
  (if babashka?
    ((:delete bb-fs) p)
    (do ((:rm-rf mn-fs) (str p)) nil)))

(defn which
  "The absolute path to cmd on PATH, or nil when not found."
  [cmd]
  (if babashka?
    ((:which bb-fs) cmd)
    ((:which mn-fs) cmd)))

;; --- process execution ---------------------------------------------------

(defn shell
  "Run cmd (with optional args) under opts, returning {:out :err :exit}.
  Never throws on non-zero exit. When the first argument is a map it
  is taken as opts; otherwise opts is empty."
  [cmd & args]
  (let [opts     (if (map? cmd) cmd {})
        real-cmd (if (map? cmd) (first args) cmd)
        real-args (if (map? cmd) (rest args) args)]
    (if babashka?
      (let [result (apply bb-proc
                          (merge {:out :string :err :string :continue true} opts)
                          (map str (cons real-cmd real-args)))]
        {:out (:out result) :err (:err result) :exit (:exit result)})
      (if (map? cmd)
        (apply (:run mn-fs) opts real-cmd real-args)
        (apply (:run mn-fs) cmd args)))))

;; --- JSON (hooks) --------------------------------------------------------

(defn json-parse
  "Parse JSON string s to Clojure data. Keywordizes keys when keywordize?
  is true (the default). Uses cheshire when available, else spine.json."
  ([s]
   (json-parse s true))
  ([s keywordize?]
   (if has-json?
     ((@(ns-resolve 'cheshire.core 'parse-string)) s keywordize?)
     (json/parse s keywordize?))))

(defn json-encode
  "Encode data as a JSON string. Uses cheshire when available, else
  spine.json."
  [data]
  (if has-json?
    ((@(ns-resolve 'cheshire.core 'encode)) data)
    (json/emit data)))

;; --- stdin (hooks) -------------------------------------------------------

(defn slurp-stdin
  "Read all of stdin as a string. Uses read-line so it works under mino
  (which lacks *in* as a Reader) and Babashka alike."
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
  (if babashka?
    (let [md (java.security.MessageDigest/getInstance "SHA-256")
          bs (.digest md (.getBytes ^String s "UTF-8"))]
      (str "sha256:"
           (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))
    (str "sha256:" ((:sha256 mn-fs) s))))
