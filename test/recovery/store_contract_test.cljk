(ns recovery.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [recovery.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Community MRF Batch 4" (:batch-name (store/batch s "batch-1"))))
      (is (= "JPN" (:jurisdiction (store/batch s "batch-1"))))
      (is (= 2.0 (:contamination-percentage (store/batch s "batch-1"))))
      (is (= 5.0 (:contamination-max-allowed (store/batch s "batch-1"))))
      (is (false? (:contamination-flag-unresolved? (store/batch s "batch-1"))))
      (is (= 8.0 (:contamination-percentage (store/batch s "batch-3"))))
      (is (true? (:contamination-flag-unresolved? (store/batch s "batch-4"))))
      (is (false? (:material-grade-certified? (store/batch s "batch-1"))))
      (is (false? (:impact-report-published? (store/batch s "batch-1"))))
      (is (= ["batch-1" "batch-2" "batch-3" "batch-4"]
             (mapv :id (store/all-batches s))))
      (is (nil? (store/contamination-screen-of s "batch-1")))
      (is (nil? (store/grading-verification-of s "batch-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/certification-history s)))
      (is (= [] (store/report-history s)))
      (is (zero? (store/next-certification-sequence s "JPN")))
      (is (zero? (store/next-report-sequence s "JPN")))
      (is (false? (store/batch-already-certified? s "batch-1")))
      (is (false? (store/batch-already-published? s "batch-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :batch/upsert
                                 :value {:id "batch-1" :batch-name "Sakura Community MRF Batch 4"}})
        (is (= "Sakura Community MRF Batch 4" (:batch-name (store/batch s "batch-1"))))
        (is (= 2.0 (:contamination-percentage (store/batch s "batch-1"))) "unrelated field preserved"))
      (testing "verification / contamination-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["batch-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/grading-verification-of s "batch-1")))
        (store/commit-record! s {:effect :contamination-screen/set :path ["batch-1"]
                                 :payload {:batch-id "batch-1" :verdict :resolved}})
        (is (= {:batch-id "batch-1" :verdict :resolved} (store/contamination-screen-of s "batch-1"))))
      (testing "material-grade certification drafts a record and advances the sequence"
        (store/commit-record! s {:effect :batch/mark-certified :path ["batch-1"]})
        (is (= "JPN-GRD-000000" (get (first (store/certification-history s)) "record_id")))
        (is (= "material-grade-certification-draft" (get (first (store/certification-history s)) "kind")))
        (is (true? (:material-grade-certified? (store/batch s "batch-1"))))
        (is (= 1 (count (store/certification-history s))))
        (is (= 1 (store/next-certification-sequence s "JPN")))
        (is (true? (store/batch-already-certified? s "batch-1")))
        (is (false? (store/batch-already-certified? s "batch-2"))))
      (testing "impact report drafts a record and advances the sequence"
        (store/commit-record! s {:effect :batch/mark-published :path ["batch-1"]})
        (is (= "JPN-IMP-000000" (get (first (store/report-history s)) "record_id")))
        (is (= "impact-report-draft" (get (first (store/report-history s)) "kind")))
        (is (true? (:impact-report-published? (store/batch s "batch-1"))))
        (is (= 1 (count (store/report-history s))))
        (is (= 1 (store/next-report-sequence s "JPN")))
        (is (true? (store/batch-already-published? s "batch-1")))
        (is (false? (store/batch-already-published? s "batch-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/batch s "nope")))
    (is (= [] (store/all-batches s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/certification-history s)))
    (is (= [] (store/report-history s)))
    (is (zero? (store/next-certification-sequence s "JPN")))
    (is (zero? (store/next-report-sequence s "JPN")))
    (store/with-batches s {"x" {:id "x" :batch-name "n" :contamination-percentage 2.0
                               :contamination-max-allowed 5.0
                               :contamination-flag-unresolved? false
                               :material-grade-certified? false :impact-report-published? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:batch-name (store/batch s "x"))))))
