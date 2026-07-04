(ns spine.json
  "Minimal JSON parser and emitter for the hook protocol. Handles
  the hook protocol JSON: objects, arrays, strings, numbers, booleans, and
  null. Pure Clojure, no external dependencies."
  (:require [clojure.string :as str]))

;;;; parser

(declare parse-value)

(defn- skip-ws [s pos]
  (if (and (< pos (count s))
           (contains? #{\space \tab \newline \return} (get s pos)))
    (recur s (inc pos))
    pos))

(defn- parse-raw-string
  "Parse a quoted string starting at pos (s[pos] must be \"). Returns
  [string new-pos]."
  [s pos]
  (letfn [(step [p buf]
            (if (>= p (count s))
              (throw (ex-info "JSON: unterminated string" {}))
              (let [c (get s p)]
                (cond
                  (= c \") [(apply str buf) (inc p)]
                  (= c \\)
                  (let [nc (get s (inc p))]
                    (case nc
                      \"  (step (+ p 2) (conj buf \"))
                      \\  (step (+ p 2) (conj buf \\))
                      \/  (step (+ p 2) (conj buf \/))
                      \n  (step (+ p 2) (conj buf \newline))
                      \t  (step (+ p 2) (conj buf \tab))
                      \r  (step (+ p 2) (conj buf \return))
                      \b  (step (+ p 2) (conj buf \backspace))
                      \f  (step (+ p 2) (conj buf \formfeed))
                      \u  (let [hex (subs s (+ p 2) (+ p 6))
                                cp (read-string (str "16r" hex))]
                            (step (+ p 6) (conj buf (char cp))))
                      (throw (ex-info (str "JSON: bad escape \\" nc) {}))))
                  :else (step (inc p) (conj buf c))))))]
    (step (inc pos) [])))

(defn- parse-number [s pos]
  (let [m (re-find #"^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?"
                   (subs s pos))]
    (when (nil? m)
      (throw (ex-info "JSON: expected number" {:pos pos})))
    (let [raw m
          val (if (re-find #"[.eE]" raw)
                (read-string raw)
                (read-string raw))]
      [val (+ pos (count raw))])))

(defn- parse-literal [s pos literal value]
  (let [end (+ pos (count literal))]
    (if (= (subs s pos end) literal)
      [value end]
      (throw (ex-info (str "JSON: expected " literal) {:pos pos})))))

(defn- parse-array [s pos]
  (letfn [(step [p acc]
            (let [p (skip-ws s p)]
              (cond
                (>= p (count s))
                (throw (ex-info "JSON: unterminated array" {}))
                (= (get s p) \])
                [(vec acc) (inc p)]
                :else
                (let [[v np] (parse-value s p)
                      np (skip-ws s np)]
                  (cond
                    (= (get s np) \,) (step (inc np) (conj acc v))
                    (= (get s np) \]) [(vec (conj acc v)) (inc np)]
                    :else (throw (ex-info "JSON: expected , or ]" {:pos np})))))))]
    (step (inc pos) [])))

(defn- parse-object [s pos keywordize?]
  (letfn [(step [p m]
            (let [p (skip-ws s p)]
              (cond
                (>= p (count s))
                (throw (ex-info "JSON: unterminated object" {}))
                (= (get s p) \})
                [m (inc p)]
                :else
                (let [p (skip-ws s p)
                      _ (when (not= (get s p) \")
                          (throw (ex-info "JSON: expected string key" {:pos p})))
                      [kraw np] (parse-raw-string s p)
                      key (if keywordize? (keyword kraw) kraw)
                      np (skip-ws s np)
                      _ (when (not= (get s np) \:)
                          (throw (ex-info "JSON: expected :" {:pos np})))
                      [v np2] (parse-value s (inc np))
                      np2 (skip-ws s np2)]
                  (cond
                    (= (get s np2) \,) (step (inc np2) (assoc m key v))
                    (= (get s np2) \}) [(assoc m key v) (inc np2)]
                    :else (throw (ex-info "JSON: expected , or }" {:pos np2})))))))]
    (step (inc pos) {})))

(defn- parse-value [s pos]
  (let [pos (skip-ws s pos)]
    (when (>= pos (count s))
      (throw (ex-info "JSON: unexpected end" {})))
    (let [c (get s pos)]
      (cond
        (= c \") (parse-raw-string s pos)
        (= c \{) (parse-object s pos true)
        (= c \[) (parse-array s pos)
        (= c \t) (parse-literal s pos "true" true)
        (= c \f) (parse-literal s pos "false" false)
        (= c \n) (parse-literal s pos "null" nil)
        (or (= c \-) (and (>= (int c) (int \0)) (<= (int c) (int \9))))
        (parse-number s pos)
        :else (throw (ex-info (str "JSON: unexpected " c) {:pos pos}))))))

(defn parse
  "Parse JSON string s to Clojure data. Keywordizes object keys by default."
  ([s] (parse s true))
  ([s keywordize?]
   (let [[v pos] (parse-value s 0)
         pos (skip-ws s pos)]
     (when (< pos (count s))
       (throw (ex-info "JSON: trailing data" {:pos pos})))
     v)))

;;;; emitter

(defn- escape-char [c]
  (case c
    \" "\\\""
    \\ "\\\\"
    \newline "\\n"
    \tab "\\t"
    \return "\\r"
    \backspace "\\b"
    \formfeed "\\f"
    nil))

(defn- emit-string [s]
  (str "\""
       (apply str
              (for [c s]
                (or (escape-char c)
                    (if (< (int c) 32)
                      (format "\\u%04x" (int c))
                      (str c)))))
       "\""))

(defn- emit-key [k]
  (emit-string (cond
                 (string? k) k
                 (keyword? k) (name k)
                 :else (str k))))

(defn emit
  "Emit Clojure data as a JSON string."
  [v]
  (cond
    (nil? v) "null"
    (instance? Boolean v) (if v "true" "false")
    (number? v) (str v)
    (string? v) (emit-string v)
    (keyword? v) (emit-string (name v))
    (map? v)
    (str "{"
         (str/join ","
                   (for [[k val] v]
                     (str (emit-key k) ":" (emit val))))
         "}")
    (or (vector? v) (seq? v))
    (str "["
         (str/join ","
                   (for [item v]
                     (emit item)))
         "]")
    :else (emit-string (str v))))
