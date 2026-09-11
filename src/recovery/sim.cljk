(ns recovery.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean batch through
  intake -> grading verification -> contamination-flag screening ->
  material-grade-certification proposal (always escalates) -> human
  approval -> commit, then through impact-report proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds
  (a jurisdiction with no spec-basis, an over-contaminated batch, an
  unresolved contamination flag screened directly via `:contamination/
  screen` [never via an actuation op against an unscreened batch --
  see this actor's own governor ns docstring / the lesson
  `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s and
  `aerospace`'s ADR-0001s already recorded], and a double material-
  grade-certification/impact-report-publication of an already-
  processed batch) that never reach a human at all, and prints the
  audit ledger + the draft material-grade-certification and impact-
  report records."
  (:require [langgraph.graph :as g]
            [recovery.store :as store]
            [recovery.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :recovery-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== batch/intake batch-1 (JPN, clean; contamination within max allowed, no flag) ==")
    (println (exec! actor "t1" {:op :batch/intake :subject "batch-1"
                                :patch {:id "batch-1" :batch-name "Sakura Community MRF Batch 4"}} operator))

    (println "== grading/verify batch-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :grading/verify :subject "batch-1"} operator))
    (println (approve! actor "t2"))

    (println "== contamination/screen batch-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :contamination/screen :subject "batch-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/certify-material-grade batch-1 (always escalates -- actuation/certify-material-grade) ==")
    (let [r (exec! actor "t4" {:op :actuation/certify-material-grade :subject "batch-1"} operator)]
      (println r)
      (println "-- human recovery operator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/publish-impact-report batch-1 (always escalates -- actuation/publish-impact-report) ==")
    (let [r (exec! actor "t5" {:op :actuation/publish-impact-report :subject "batch-1"} operator)]
      (println r)
      (println "-- human recovery operator approves --")
      (println (approve! actor "t5")))

    (println "== grading/verify batch-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :grading/verify :subject "batch-2" :no-spec? true} operator))

    (println "== grading/verify batch-3 (escalates -- human approves; sets up the over-contamination test) ==")
    (println (exec! actor "t7" {:op :grading/verify :subject "batch-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/certify-material-grade batch-3 (8.0% exceeds 5.0% max-allowed -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/certify-material-grade :subject "batch-3"} operator))

    (println "== contamination/screen batch-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :contamination/screen :subject "batch-4"} operator))

    (println "== actuation/certify-material-grade batch-1 AGAIN (double-certification -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/certify-material-grade :subject "batch-1"} operator))

    (println "== actuation/publish-impact-report batch-1 AGAIN (double-publication -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/publish-impact-report :subject "batch-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft material-grade-certification records ==")
    (doseq [r (store/certification-history db)] (println r))

    (println "== draft impact-report records ==")
    (doseq [r (store/report-history db)] (println r))))
