(ns recovery.registry-test
  (:require [clojure.test :refer [deftest is]]
            [recovery.registry :as r]))

;; ----------------------------- contamination-percentage-exceeds-maximum? -----------------------------

(deftest not-exceeding-when-at-or-below-maximum
  (is (not (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 2.0 :contamination-max-allowed 5.0})))
  (is (not (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 5.0 :contamination-max-allowed 5.0})))
  (is (not (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 0.0 :contamination-max-allowed 5.0}))))

(deftest exceeding-when-above-maximum
  (is (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 8.0 :contamination-max-allowed 5.0}))
  (is (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 5.01 :contamination-max-allowed 5.0})))

(deftest exceeding-is-false-on-missing-fields
  (is (not (r/contamination-percentage-exceeds-maximum? {})))
  (is (not (r/contamination-percentage-exceeds-maximum? {:contamination-percentage 8.0}))))

;; ----------------------------- register-material-grade-certification -----------------------------

(deftest certification-is-a-draft-not-a-real-certification
  (let [result (r/register-material-grade-certification "batch-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certification-assigns-certification-number
  (let [result (r/register-material-grade-certification "batch-1" "JPN" 7)]
    (is (= (get result "certification_number") "JPN-GRD-000007"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "material-grade-certification-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certification-validation-rules
  (is (thrown? Exception (r/register-material-grade-certification "" "JPN" 0)))
  (is (thrown? Exception (r/register-material-grade-certification "batch-1" "" 0)))
  (is (thrown? Exception (r/register-material-grade-certification "batch-1" "JPN" -1))))

;; ----------------------------- register-impact-report -----------------------------

(deftest report-is-a-draft-not-a-real-publication
  (let [result (r/register-impact-report "batch-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest report-assigns-report-number
  (let [result (r/register-impact-report "batch-1" "JPN" 3)]
    (is (= (get result "report_number") "JPN-IMP-000003"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "impact-report-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest report-validation-rules
  (is (thrown? Exception (r/register-impact-report "" "JPN" 0)))
  (is (thrown? Exception (r/register-impact-report "batch-1" "" 0)))
  (is (thrown? Exception (r/register-impact-report "batch-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-material-grade-certification "batch-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-material-grade-certification "batch-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-GRD-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-GRD-000001" (get-in hist2 [1 "record_id"])))))
