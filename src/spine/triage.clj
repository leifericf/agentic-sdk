(ns spine.triage
  "Findings in, punch list out. A pure, deterministic transformation: no
  editorial judgment, only the fixed ordering doctrine. Dedupe on
  [file evidence rule] merging reporters and keeping the most severe,
  drop protected-idiom findings, convert rule-less opinions to queries,
  order by editing tier then severity then file order, renumber FINDING-N.
  Reviewers and lint write findings/*.edn; this is their sole consumer.

  Tier and severity come from the reporter. A reviewer sets :level
  (:correctness | :factoring | :style) and :severity (:high | :medium |
  :low). Findings without :level (lint, render) fall back to their
  dimension's tier. Severity normalizes the lint task's uppercase vocabulary
  (:CRITICAL/:SIGNIFICANT -> :high, :MODERATE -> :medium, :MINOR -> :low)
  so one vocabulary reaches the punch list.

  Ordering tiers: correctness, then factoring, then style, then lint."
  (:require [spine.host :as host]
            [spine.repo :as repo]
            [clojure.string :as str]))

(def ^:private dimension-tier
  "Fallback only: when a finding carries no :level, derive its tier from
  its dimension. Reporters set :level; this covers lint and render findings."
  {:correctness :correctness, :security :correctness, :conformance :correctness
   :factoring :factoring, :performance :factoring, :portability :factoring, :memory :factoring
   :style :style, :clarity :style
   :lint :lint, :render :lint})

(def ^:private tier-rank
  {:correctness 0, :factoring 1, :style 2, :lint 3})

(def ^:private severity-normalize
  "Map the lint task's uppercase vocabulary onto the reviewer vocabulary."
  {:CRITICAL :high, :SIGNIFICANT :high
   :MODERATE :medium, :MINOR :low
   :high :high, :medium :medium, :low :low})

(def ^:private severity-rank {:high 0, :medium 1, :low 2})

(defn- norm-sev [s] (severity-normalize (keyword s) :low))

(defn- dedupe-key [f]
  ;; Flat shape: file and evidence live on the finding, not under :location.
  [(:file f) (str (:evidence f)) (:rule f)])

(defn- merge-group
  "Collapse same-span same-rule findings: most severe severity, every
  reporter, the suggestion from the most severe report."
  [fs]
  (let [ranked (sort-by (comp severity-rank norm-sev :severity) fs)
        most (first ranked)]
    (assoc most
           :severity (norm-sev (:severity most))
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

(defn- tier-of [f]
  (or (:level f) (dimension-tier (:dimension f) :lint)))

(defn triage
  "Findings (a seq of finding maps) -> {:findings :queries :counts :by-file}.
  protected-idioms is an optional seq of strings dropped as a backstop.
  Pure: same inputs, same output, order-independent (group-by before sort).
  Honors each finding's :level when set; falls back to the dimension tier."
  [raw & {:keys [protected-idioms] :or {protected-idioms []}}]
  (let [;; rule-less opinions become queries; protected idioms vanish
        {queries true keepers false}
        (group-by #(str/blank? (str (:rule %))) raw)
        kept (remove #(protected? protected-idioms (:evidence %)) keepers)
        ;; dedupe same file + same evidence + same rule
        deduped (->> (group-by dedupe-key kept) vals (map merge-group))
        ;; tier, then severity, then file, then line
        ordered (sort-by (fn [f]
                           [(tier-rank (tier-of f) 99)
                            (severity-rank (norm-sev (:severity f)) 99)
                            (str (:file f))
                            (str (or (:line f) 0))])
                         deduped)
        numbered (map-indexed
                  (fn [i f]
                    (assoc f
                           :id (str "FINDING-" (inc i))
                           :level (tier-of f)
                           :severity (norm-sev (:severity f))))
                  ordered)]
    {:findings (vec numbered)
     :queries (vec (map ->query queries))
     :by-file (into {} (map (fn [[fl fs]] [fl (mapv :id fs)])
                            (group-by :file numbered)))
     :counts {:total (count numbered)
              :by-level (frequencies (map :level numbered))
              :by-severity (frequencies (map :severity numbered))}}))

;;;; rendering

(defn- finding-block [f]
  (str/join "\n"
            [(str "## " (:id f) ": "
                  (or (:suggestion f) (:evidence f)))
             (str "- Dimension: " (name (:dimension f)))
             (str "- Severity: " (name (:severity f)))
             (str "- Level: " (name (:level f)))
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

;;;; file-driven entry point

(defn triage!
  "Read every findings/*.edn, triage, and write triage/punch-list.edn and
  .md, then consume (delete) the inputs. Reads protected-idioms.edn when
  present. Returns a bounded summary."
  [root]
  (let [wd       (repo/working-dir root)
        findings (vec (repo/read-collection (host/path wd "findings")))
        idioms   (or (repo/read-edn (host/path wd "protected-idioms.edn")) [])
        punch    (triage findings :protected-idioms idioms)
        out-dir  (host/path wd "triage")]
    (repo/write-edn! (host/path out-dir "punch-list.edn") punch)
    (repo/write-text! (host/path out-dir "punch-list.md")
                      (render-punch-list punch))
    (repo/clear-collection! (host/path wd "findings"))
    (merge (:counts punch) {:queries (count (:queries punch))})))

(defn -main
  "agentic triage [ROOT] - writes the punch list, prints the count line.
  Exit 0 clean (no findings), 1 findings present, 2 usage."
  [& args]
  (let [root (or (first args) ".")
        {:keys [total] :as summary} (triage! root)]
    (println (format "triage: %d findings, %d queries"
                     total (:queries summary)))
    (System/exit (if (pos? total) 1 0))))
