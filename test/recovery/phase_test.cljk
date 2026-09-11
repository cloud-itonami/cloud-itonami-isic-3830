(ns recovery.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/certify-material-grade`/`:actuation/publish-
  impact-report` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [recovery.phase :as phase]))

(deftest certify-material-grade-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real material-grade certification"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/certify-material-grade))
          (str "phase " n " must not auto-commit :actuation/certify-material-grade")))))

(deftest publish-impact-report-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real impact-report publication"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/publish-impact-report))
          (str "phase " n " must not auto-commit :actuation/publish-impact-report")))))

(deftest contamination-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :contamination/screen))
          (str "phase " n " must not auto-commit :contamination/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":batch/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:batch/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :batch/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/certify-material-grade} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/publish-impact-report} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :batch/intake} :commit)))))
