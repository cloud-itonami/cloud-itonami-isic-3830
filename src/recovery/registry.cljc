(ns recovery.registry
  "Pure-function material-grade-certification + impact-report record
  construction -- an append-only materials-recovery-operator book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a material-grade-
  certification or impact-report reference number -- every operator/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `recovery.facts` uses.

  `contamination-percentage-exceeds-maximum?` is the FOURTH instance
  of this fleet's MAXIMUM-ceiling check family (`facility.registry/
  occupancy-exceeds-capacity?` established the first, `school.
  registry/class-size-exceeds-maximum?` the second, `card.registry/
  settlement-amount-exceeds-authorized?` the third), applying the SAME
  single-value-vs-ceiling-comparison shape to a batch's own measured
  contamination percentage against the batch's own recorded maximum-
  allowed-for-grade threshold.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real scale/scanner/MRF (materials recovery facility)
  control system. It builds the RECORD an operator would keep, not
  the act of certifying the material grade or publishing the impact
  report itself (that is `recovery.operation`'s `:actuation/certify-
  material-grade`/`:actuation/publish-impact-report`, always human-
  gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  operator's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn contamination-percentage-exceeds-maximum?
  "Does `batch`'s own `:contamination-percentage` exceed its own
  recorded `:contamination-max-allowed` ceiling for its target grade?
  A pure ground-truth check against the batch's own permanent fields
  -- no upstream comparison needed. The FOURTH instance of this
  fleet's MAXIMUM-ceiling check family (see ns docstring)."
  [{:keys [contamination-percentage contamination-max-allowed]}]
  (and (number? contamination-percentage) (number? contamination-max-allowed)
       (> contamination-percentage contamination-max-allowed)))

(defn contamination-percentage-exceeds-maximum-checkable?
  "Are both sides of `contamination-percentage-exceeds-maximum?` actually recorded?

  That predicate answers only `over` / `not over`, and its
  `(and (number? ...) (number? ...) ...)` guard made every un-recorded
  case fall through as `not over` -- an entity missing either figure
  passed the limit check silently. Callers must ask this first:
  un-checkable is not within limits."
  [{:keys [contamination-percentage contamination-max-allowed]}]
  (boolean (and (number? contamination-percentage) (number? contamination-max-allowed))))

(defn register-material-grade-certification
  "Validate + construct the MATERIAL-GRADE-CERTIFICATION registration
  DRAFT -- the operator's own act of certifying a real material
  grade for a batch ahead of sale or reuse. Pure function -- does not
  touch any real scale/scanner/MRF control system; it builds the
  RECORD an operator would keep. `recovery.governor` independently
  re-verifies the batch's own contamination-percentage sufficiency
  against its own recorded ceiling, and blocks a double-certification
  for the same batch, before this is ever allowed to commit."
  [batch-id jurisdiction sequence]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "material-grade-certification: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "material-grade-certification: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "material-grade-certification: sequence must be >= 0" {})))
  (let [cert-number (str (str/upper-case jurisdiction) "-GRD-" (zero-pad sequence 6))
        record {"record_id" cert-number
                "kind" "material-grade-certification-draft"
                "batch_id" batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certification_number" cert-number
     "certificate" (unsigned-certificate "MaterialGradeCertification" cert-number cert-number)}))

(defn register-impact-report
  "Validate + construct the IMPACT-REPORT registration DRAFT -- the
  operator's own act of publishing a real impact/ESG report referring
  to a batch's own weighed and verified records. Pure function -- does
  not touch any real scale/scanner/MRF control system; it builds the
  RECORD an operator would keep. `recovery.governor` independently
  re-verifies the batch's own contamination-flag resolution status,
  and blocks a double-publication for the same batch, before this is
  ever allowed to commit."
  [batch-id jurisdiction sequence]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "impact-report: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "impact-report: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "impact-report: sequence must be >= 0" {})))
  (let [report-number (str (str/upper-case jurisdiction) "-IMP-" (zero-pad sequence 6))
        record {"record_id" report-number
                "kind" "impact-report-draft"
                "batch_id" batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "report_number" report-number
     "certificate" (unsigned-certificate "ImpactReport" report-number report-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
