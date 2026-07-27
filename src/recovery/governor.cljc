(ns recovery.governor
  "Traceability Governor -- the independent compliance layer that
  earns the Recovery Advisor the right to commit. The LLM has no
  notion of materials-recovery grading law, whether a batch's own
  measured contamination percentage actually stays within its own
  recorded maximum-allowed ceiling, whether a contamination flag
  against the batch has actually stayed unresolved, or when an act
  stops being a draft and becomes a real-world material-grade
  certification or impact-report publication, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD --
  the materials-recovery-operator analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated grading-standard spec-basis, incomplete evidence, an
  over-contaminated batch, an unresolved contamination flag, or a
  double certification/publication). The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `recovery.phase`: for `:stake
  :actuation/certify-material-grade`/`:actuation/publish-impact-report`
  (a real economic/ESG claim) NO phase ever allows auto-commit either.
  Two independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the grading proposal cite an
                                       OFFICIAL source (`recovery.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/certify-
                                       material-grade`/`:actuation/
                                       publish-impact-report`, has the
                                       batch actually been graded with
                                       a full weight/scale-record/
                                       material-composition-scan-
                                       record/contamination-inspection-
                                       record/buyer-specification-
                                       match-record evidence checklist
                                       on file?
    3. Contamination exceeds
       maximum                       -- for `:actuation/certify-
                                       material-grade`, INDEPENDENTLY
                                       recompute whether the batch's
                                       own contamination percentage
                                       exceeds its own recorded
                                       maximum-allowed-for-grade
                                       ceiling (`recovery.registry/
                                       contamination-percentage-
                                       exceeds-maximum?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The
                                       FOURTH instance of this fleet's
                                       MAXIMUM-ceiling check family
                                       (`facility.governor/occupancy-
                                       exceeds-capacity-violations`/
                                       `school.governor/class-size-
                                       exceeds-maximum-violations`/
                                       `card.governor/settlement-
                                       amount-exceeds-authorized-
                                       violations` established the
                                       first three).
    4. Contamination flag unresolved -- reported by THIS proposal itself
                                       (a `:contamination/screen` that
                                       just found an unresolved flag),
                                       or already on file for the
                                       batch (`:contamination/screen`/
                                       `:actuation/publish-impact-
                                       report`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty-seven prior siblings)...
                                       established -- the TWENTY-
                                       EIGHTH distinct application of
                                       this exact discipline, and the
                                       FIRST specifically for a
                                       contamination-flag concept.
                                       Like the seventeen most recent
                                       siblings' equivalent checks,
                                       this is exercised in tests/demo
                                       via `:contamination/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened batch
                                       -- see this ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       certify-material-grade`/
                                       `:actuation/publish-impact-
                                       report` (REAL economic/ESG
                                       claims) -> escalate.

  Two more guards, double-certification/double-publication
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-certified-violations`/`already-published-violations` refuse
  to certify a grade/publish a report for the SAME batch twice, off
  dedicated `:material-grade-certified?`/`:impact-report-published?`
  facts (never a `:status` value) -- the SAME 'check a dedicated
  boolean, not status' discipline every prior sibling governor's
  guards establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320)."
  (:require [recovery.facts :as facts]
            [recovery.registry :as registry]
            [recovery.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Certifying a real material grade and publishing a real impact
  report are the two real-world actuation events this actor performs
  -- a two-member set, matching every prior dual-actuation sibling's
  shape. Both are POSITIVE actuations (issuing/finalizing a record),
  matching this fleet's majority actuation shape."
  #{:actuation/certify-material-grade :actuation/publish-impact-report})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:grading/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  materials-recovery grading requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:grading/verify :actuation/certify-material-grade :actuation/publish-impact-report} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は品位基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/certify-material-grade`/`:actuation/publish-impact-
  report`, the jurisdiction's required weight/scale-record/material-
  composition-scan-record/contamination-inspection-record/buyer-
  specification-match-record evidence must actually be satisfied --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/certify-material-grade :actuation/publish-impact-report} op)
    (let [b (store/batch st subject)
          verification (store/grading-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(計量記録/材料組成スキャン記録/汚染検査記録/買主仕様適合記録等)が充足していない状態での提案"}]))))

(defn- contamination-exceeds-maximum-violations
  "For `:actuation/certify-material-grade`, INDEPENDENTLY recompute
  whether the batch's own contamination percentage exceeds its own
  recorded maximum-allowed-for-grade ceiling via `recovery.registry/
  contamination-percentage-exceeds-maximum?` -- needs no proposal
  inspection or stored-verdict lookup at all, since its inputs are
  permanent ground-truth fields already on the batch."
  [{:keys [op subject]} st]
  (when (= op :actuation/certify-material-grade)
    (let [b (store/batch st subject)]
      (cond
        ;; Either figure missing or non-numeric: the limit cannot be
        ;; evaluated, so it is not "within limits". This used to fall
        ;; through as "not over" and proceed.
        ;; Only when the entity EXISTS: a missing entity is a different
        ;; violation that another gate owns, and firing here would mask it.
        (and b (not (registry/contamination-percentage-exceeds-maximum-checkable? b)))
        [{:rule :contamination-exceeds-maximum
          :detail "上限判定に必要な値が記録されていない -- 限度内と断定できないため進めない"}]

        (registry/contamination-percentage-exceeds-maximum? b)
        [{:rule :contamination-exceeds-maximum
          :detail (str subject " の汚染率(" (:contamination-percentage b)
                      "%)が許容上限(" (:contamination-max-allowed b) "%)を超過")}]))))

(defn- contamination-flag-unresolved-violations
  "An unresolved contamination flag -- reported by THIS proposal (e.g.
  a `:contamination/screen` that itself just found one), or already on
  file in the store for the batch (`:contamination/screen`/
  `:actuation/publish-impact-report`) -- is a HARD, un-overridable
  hold. Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        batch-id (when (contains? #{:contamination/screen :actuation/publish-impact-report} op) subject)
        hit-on-file? (and batch-id (= :unresolved (:verdict (store/contamination-screen-of st batch-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :contamination-flag-unresolved
        :detail "未解決の汚染フラグがある状態でのインパクトレポート公開提案は進められない"}])))

(defn- already-certified-violations
  "For `:actuation/certify-material-grade`, refuses to certify a grade
  for the SAME batch twice, off a dedicated `:material-grade-
  certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/certify-material-grade)
    (when (store/batch-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に品位認証済み")}])))

(defn- already-published-violations
  "For `:actuation/publish-impact-report`, refuses to publish an
  impact report for the SAME batch twice, off a dedicated `:impact-
  report-published?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/publish-impact-report)
    (when (store/batch-already-published? st subject)
      [{:rule :already-published
        :detail (str subject " は既にインパクトレポート公開済み")}])))

(defn check
  "Censors a Recovery Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (contamination-exceeds-maximum-violations request st)
                           (contamination-flag-unresolved-violations request proposal st)
                           (already-certified-violations request st)
                           (already-published-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
