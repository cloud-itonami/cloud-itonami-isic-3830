(ns recovery.governor-contract-test
  "The governor contract as executable tests -- the materials-
  recovery-operator analog of `cloud-itonami-isic-6512`'s `casualty.
  governor-contract-test`. The single invariant under test:

    Recovery Advisor never certifies a material grade or publishes an
    impact report the Traceability Governor would reject,
    `:actuation/certify-material-grade`/`:actuation/publish-impact-
    report` NEVER auto-commit at any phase, `:batch/intake` (no direct
    capital risk) MAY auto-commit when clean, and every decision
    (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [recovery.store :as store]
            [recovery.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :recovery-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a grading
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :grading/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through contamination-flag screening -> approve,
  leaving a screening on file. Only safe to call for a batch whose
  flag status has already resolved -- an unresolved flag HARD-holds
  the screen itself (see
  `contamination-flag-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :contamination/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :batch/intake :subject "batch-1"
                   :patch {:id "batch-1" :batch-name "Sakura Community MRF Batch 4"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Community MRF Batch 4" (:batch-name (store/batch db "batch-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest grading-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :grading/verify :subject "batch-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/grading-verification-of db "batch-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a grading/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :grading/verify :subject "batch-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/grading-verification-of db "batch-1")) "no verification written"))))

(deftest certify-material-grade-without-verification-is-held
  (testing "actuation/certify-material-grade before any grading verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/certify-material-grade :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest contamination-exceeds-maximum-is-held
  (testing "a batch whose own contamination percentage exceeds its own maximum-allowed ceiling -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "batch-3")
          res (exec-op actor "t5" {:op :actuation/certify-material-grade :subject "batch-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:contamination-exceeds-maximum} (-> (store/ledger db) last :basis)))
      (is (empty? (store/certification-history db))))))

(deftest contamination-flag-is-held-and-unoverridable
  (testing "an unresolved contamination flag on a batch -> HOLD, and never reaches request-approval -- exercised via :contamination/screen DIRECTLY, not via the actuation op against an unscreened batch (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's and aerospace's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :contamination/screen :subject "batch-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:contamination-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/contamination-screen-of db "batch-4")) "no clearance written"))))

(deftest certify-material-grade-always-escalates-then-human-decides
  (testing "a clean, fully-verified, under-max batch still ALWAYS interrupts for human approval -- actuation/certify-material-grade is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "batch-1")
          r1 (exec-op actor "t7" {:op :actuation/certify-material-grade :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certification record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:material-grade-certified? (store/batch db "batch-1"))))
          (is (= 1 (count (store/certification-history db))) "one draft certification record"))))))

(deftest publish-impact-report-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-flag batch still ALWAYS interrupts for human approval -- actuation/publish-impact-report is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "batch-1")
          _ (screen! actor "t8pre2" "batch-1")
          r1 (exec-op actor "t8" {:op :actuation/publish-impact-report :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, report record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:impact-report-published? (store/batch db "batch-1"))))
          (is (= 1 (count (store/report-history db))) "one draft report record"))))))

(deftest certify-material-grade-double-certification-is-held
  (testing "certifying the same batch's material grade twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "batch-1")
          _ (exec-op actor "t9a" {:op :actuation/certify-material-grade :subject "batch-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/certify-material-grade :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certification-history db))) "still only the one earlier certification"))))

(deftest publish-impact-report-double-publication-is-held
  (testing "publishing the same batch's impact report twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "batch-1")
          _ (screen! actor "t10pre2" "batch-1")
          _ (exec-op actor "t10a" {:op :actuation/publish-impact-report :subject "batch-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/publish-impact-report :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-published} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/report-history db))) "still only the one earlier publication"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :batch/intake :subject "batch-1"
                          :patch {:id "batch-1" :batch-name "Sakura Community MRF Batch 4"}} operator)
      (exec-op actor "b" {:op :grading/verify :subject "batch-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
