(ns spine.triage
  "Findings in, punch list out. A pure, deterministic transformation: no
  editorial judgment, only the fixed ordering doctrine. Dedupe on
  [file evidence rule] merging reporters and keeping the most severe,
  drop protected-idiom findings, convert rule-less opinions to queries,
  order by editing level then severity then file order, renumber FINDING-N.
  Reviewers and lint write findings/*.edn; this is their sole consumer.

  Software level mapping:
    1 correctness / security / conformance
    2 factoring / performance / portability / memory
    3 style / clarity
    4 lint / render"
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [spine.core :as core]))

(def ^:private dimension-level
  {:correctness 1 :security 1 :conformance 1
   :factoring 2 :performance 2 :portability 2 :memory 2
   :style 3 :clarity 3
   :lint 4 :render 4})

(def ^:private severity-rank
  {:CRITICAL 0 :SIGNIFICANT 1 :MODERATE 2 :MINOR 3})

(defn- dedupe-key [f]
  ;; Flat shape: file and evidence live on the finding, not under :location.
  [(:file f) (str (:evidence f)) (:rule f)])

(defn- merge-group
  "Collapse same-span same-rule findings: most severe severity, every
  reporter, the suggestion from the most severe report."
  [fs]
  (let [most (first (sort-by (comp severity-rank :severity) fs))]
    (assoc most
           :reporters (vec (distinct (keep :reporter fs)))
           :suggestion (:suggestion most))))

(defn- protected? [idioms evidence]
  (boolean (some #(and (seq %) (str/includes? (str evidence) %)) idioms)))

(defn- ->query [f]
  {:question (str (:evidence f)
                  (when (:suggestion f) (str " - " (:suggestion f))))
   :dimension (:dimension f)
   :file (:file f)
   :line (:line f)
   :reporter (:reporter f)})

(defn- level-of [f] (dimension-level (:dimension f) 99))

(defn triage
  "Findings (a seq of finding maps) -> {:findings :queries :counts :by-file}.
  protected-idioms is an optional seq of strings dropped as a backstop.
  Pure: same inputs, same output, order-independent (group-by before sort)."
  [raw & {:keys [protected-idioms] :or {protected-idioms []}}]
  (let [;; rule-less opinions become queries; protected idioms vanish
        {queries true keepers false}
        (group-by #(str/blank? (str (:rule %))) raw)
        kept (remove #(protected? protected-idioms (:evidence %)) keepers)
        ;; dedupe same file + same evidence + same rule
        deduped (->> (group-by dedupe-key kept) vals (map merge-group))
        ;; editing level, then severity, then file, then line
        ordered (sort-by (fn [f]
                           [(level-of f)
                            (severity-rank (:severity f) 99)
                            (str (:file f))
                            (str (or (:line f) 0))])
                         deduped)
        numbered (map-indexed
                  (fn [i f]
                    (assoc f
                           :id (str "FINDING-" (inc i))
                           :level (level-of f)))
                  ordered)]
    {:findings (vec numbered)
     :queries (vec (map ->query queries))
     :by-file (into {} (map (fn [[fl fs]] [fl (mapv :id fs)])
                            (group-by :file numbered)))
     :counts {:total (count numbered)
              :by-level (frequencies (map :level numbered))
              :by-severity (frequencies (map :severity numbered))}}))

;; --- rendering -----------------------------------------------------------

(defn- finding-block [f]
  (str/join "\n"
            [(str "## " (:id f) ": "
                  (or (:suggestion f) (:evidence f)))
             (str "- Dimension: " (name (:dimension f)))
             (str "- Severity: " (name (:severity f)))
             (str "- Level: " (:level f))
             (str "- File: " (:file f)
                  (when (:line f) (str ":" (:line f))))
             (str "- Evidence: \"" (:evidence f) "\"")
             (str "- Suggestion: " (or (:suggestion f) "-"))
             (str "- Rule: " (or (:rule f) "-"))
             (str "- Reporters: " (str/join ", " (:reporters f)))]))

(defn render-punch-list
  "Render the punch list as markdown, grouped by file in final order."
  [{:keys [findings queries counts]}]
  (str/join
   "\n\n"
   (concat
    ["# Punch list"]
    (for [[file fs] (sort-by key (group-by :file findings))]
      (str "## " file "\n\n"
           (str/join "\n\n" (map finding-block fs))))
    (when (seq queries)
      [(str "## Proposed queries\n\n"
            (str/join "\n" (for [q queries] (str "- " (:question q)))))])
    [(format "Total: %d findings. By level %s, by severity %s"
             (:total counts) (pr-str (:by-level counts))
             (pr-str (:by-severity counts)))])))

;; --- file-driven entry point ---------------------------------------------

(defn- read-findings [dir]
  (when (fs/exists? dir)
    (mapcat (fn [p]
              (let [data (edn/read-string (slurp (str p)))]
                (if (map? data) [data] data)))
            (sort (fs/glob dir "*.edn")))))

(defn- consume-findings!
  "Findings are consumed once triage has folded them; the punch list is the
  record. Delete the inputs so a fresh run starts clean."
  [dir]
  (doseq [p (fs/glob dir "*.edn")] (fs/delete p)))

(defn triage!
  "Read every findings/*.edn, triage, and write triage/punch-list.edn and
  .md, then consume (delete) the inputs. Reads protected-idioms.edn when
  present. Returns a bounded summary."
  [root]
  (let [wd         (core/working-dir root)
        findings   (vec (read-findings (fs/path wd "findings")))
        idiom-file (fs/path wd "protected-idioms.edn")
        idioms     (if (fs/exists? idiom-file)
                     (edn/read-string (slurp (str idiom-file))) [])
        punch      (triage findings :protected-idioms idioms)
        out-dir    (fs/path wd "triage")]
    (fs/create-dirs out-dir)
    (core/write-edn! (str (fs/path out-dir "punch-list.edn")) punch)
    (spit (str (fs/path out-dir "punch-list.md"))
          (render-punch-list punch))
    (consume-findings! (fs/path wd "findings"))
    (merge (:counts punch) {:queries (count (:queries punch))})))

(defn -main
  "bb triage [ROOT] - writes the punch list, prints the count line.
  Exit 0 clean (no findings), 1 findings present, 2 usage."
  [& args]
  (let [root (or (first args) ".")
        {:keys [total] :as summary} (triage! root)]
    (println (format "triage: %d findings, %d queries"
                     total (:queries summary)))
    (System/exit (if (pos? total) 1 0))))
