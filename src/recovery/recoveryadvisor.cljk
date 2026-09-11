(ns recovery.recoveryadvisor
  "Recovery Advisor client -- the *contained intelligence node* for
  the materials-recovery actor.

  It normalizes batch-intake, drafts a per-jurisdiction materials-
  recovery grading evidence checklist, screens batches for an
  unresolved contamination flag, drafts the material-grade-
  certification action, and drafts the impact-report-publication
  action. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real grade certification/impact-report
  publication. Every output is censored downstream by `recovery.
  governor` before anything touches the SSoT, and `:actuation/certify-
  material-grade`/`:actuation/publish-impact-report` proposals NEVER
  auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/certify-material-grade | :actuation/publish-impact-report | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [recovery.facts :as facts]
            [recovery.registry :as registry]
            [recovery.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the batch, contamination figures or jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "バッチ記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :batch/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-grading
  "Per-jurisdiction materials-recovery grading evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `recovery.facts` -- the Traceability Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [b (store/batch db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction b))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "recovery.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-contamination
  "Contamination-flag screening draft. `:contamination-flag-
  unresolved?` on the batch record injects the failure mode: the
  Traceability Governor must HOLD, un-overridably, on any unresolved
  flag."
  [db {:keys [subject]}]
  (let [b (store/batch db subject)]
    (cond
      (nil? b)
      {:summary "対象バッチ記録が見つかりません" :rationale "no batch record"
       :cites [] :effect :contamination-screen/set :value {:batch-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:contamination-flag-unresolved? b))
      {:summary    (str (:batch-name b) ": 未解決の汚染フラグを検出")
       :rationale  "スクリーニングが未解決の汚染フラグを検出。人手確認とホールドが必須。"
       :cites      [:contamination-check]
       :effect     :contamination-screen/set
       :value      {:batch-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:batch-name b) ": 未解決の汚染フラグなし")
       :rationale  "汚染フラグスクリーニング完了。"
       :cites      [:contamination-check]
       :effect     :contamination-screen/set
       :value      {:batch-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-material-grade-certification
  "Draft the actual MATERIAL-GRADE-CERTIFICATION action -- certifying
  a real material grade for a batch ahead of sale or reuse. ALWAYS
  `:stake :actuation/certify-material-grade` -- this is a REAL-WORLD
  economic act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`recovery.phase`); the governor also always escalates on
  `:actuation/certify-material-grade`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [b (store/batch db subject)]
    {:summary    (str subject " 向け品位認証提案"
                      (when b (str " (batch=" (:batch-name b) ")")))
     :rationale  (if b
                   (str "contamination-percentage=" (:contamination-percentage b)
                        " max-allowed=" (:contamination-max-allowed b))
                   "バッチ記録が見つかりません")
     :cites      (if b [subject] [])
     :effect     :batch/mark-certified
     :value      {:batch-id subject}
     :stake      :actuation/certify-material-grade
     :confidence (if (and b (not (registry/contamination-percentage-exceeds-maximum? b))) 0.9 0.3)}))

(defn- propose-impact-report
  "Draft the actual IMPACT-REPORT action -- publishing a real impact/
  ESG report referencing a batch's own weighed and verified records.
  ALWAYS `:stake :actuation/publish-impact-report` -- this is a REAL-
  WORLD act, never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`recovery.phase`); the governor also always escalates on
  `:actuation/publish-impact-report`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [b (store/batch db subject)]
    {:summary    (str subject " 向けインパクトレポート公開提案"
                      (when b (str " (batch=" (:batch-name b) ")")))
     :rationale  (if b
                   "jurisdiction-evidence-checklist referenced"
                   "バッチ記録が見つかりません")
     :cites      (if b [subject] [])
     :effect     :batch/mark-published
     :value      {:batch-id subject}
     :stake      :actuation/publish-impact-report
     :confidence (if b 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :batch/intake                        (normalize-intake db request)
    :grading/verify                      (verify-grading db request)
    :contamination/screen                (screen-contamination db request)
    :actuation/certify-material-grade    (propose-material-grade-certification db request)
    :actuation/publish-impact-report     (propose-impact-report db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは資源回収事業者の品位認証・インパクトレポート公開エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:batch/upsert|:verification/set|:contamination-screen/set|"
       ":batch/mark-certified|:batch/mark-published) "
       ":stake(:actuation/certify-material-grade か :actuation/publish-impact-report か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :grading/verify                    {:batch (store/batch st subject)}
    :contamination/screen              {:batch (store/batch st subject)}
    :actuation/certify-material-grade  {:batch (store/batch st subject)}
    :actuation/publish-impact-report   {:batch (store/batch st subject)}
    {:batch (store/batch st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Traceability Governor
  escalates/holds -- an LLM hiccup can never auto-certify a material
  grade or auto-publish an impact report."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :recoveryadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
