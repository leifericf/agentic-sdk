#!/usr/bin/env bb
;; PostToolUse hook (Claude Code): run the project formatter on the file
;; just written. Matcher: Write|Edit. Reads the path from the hook JSON;
;; picks the formatter from the descriptor's :lanes when present, else by
;; extension. Fail-soft: a missing formatter or a bad parse never blocks.

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(defn ext [path]
  (cond
    (str/ends-with? path ".cljc") "clj"
    (str/ends-with? path ".cljs") "clj"
    (str/ends-with? path ".clj")  "clj"
    (str/ends-with? path ".exs")  "ex"
    (str/ends-with? path ".ex")   "ex"
    (str/ends-with? path ".hpp")  "c"
    (str/ends-with? path ".cpp")  "c"
    (str/ends-with? path ".cc")   "c"
    (str/ends-with? path ".h")    "c"
    (str/ends-with? path ".c")    "c"
    (str/ends-with? path ".zig")  "zig"
    :else nil))

(defn lanes-formatter []
  (let [desc (try (slurp ".agentic-sdk/project.edn")
                  (catch Exception _ ""))]
    (cond
      (re-find #"clang-format" desc) "clang-format"
      (re-find #"zig fmt" desc)      "zig"
      (re-find #"cljfmt" desc)       "cljfmt"
      (re-find #"zprint" desc)       "zprint"
      (re-find #"mix format" desc)   "mix"
      :else nil)))

(def ^:private argv-for
  {"clang-format" #(vector "clang-format" "-i" %)
   "zig"          #(vector "zig" "fmt" %)
   "cljfmt"       #(vector "cljfmt" "fix" %)
   "zprint"       #(vector "zprint" "-w" %)
   "mix"          #(vector "mix" "format" %)})

(defn -main [& _]
  (let [m (try (json/parse-string (slurp *in*) true)
               (catch Exception _ nil))]
    (when-let [path (and (map? m) (-> m :tool_input :file_path))]
      (when (and (ext path) (.exists (java.io.File. path)))
        (let [fmt  (or (lanes-formatter)
                       (case (ext path)
                         "c"   "clang-format"
                         "zig" "zig"
                         "clj" "cljfmt"
                         "ex"  "mix"
                         nil))
              make (get argv-for fmt)]
          (when make
            (try (apply p/shell {:out :string :err :string :continue true}
                        (make path))
                 (catch Exception _))))))))

(-main)
