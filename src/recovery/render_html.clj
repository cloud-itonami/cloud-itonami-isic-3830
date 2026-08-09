(ns recovery.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave3 rollout ledger): this repo previously shipped only a hand-typed
  robotics placeholder at `docs/samples/operator-console.html` and had
  no generator at all. This namespace drives the REAL actor stack
  (`recovery.operation` -> `recovery.governor` -> `recovery.store`)
  through a scenario adapted from this repo's own `recovery.sim` demo
  driver (`clojure -M:dev:run`, confirmed BEFORE writing this file to
  produce a sensible ledger against the real seeded batch ids
  `batch-1`..`batch-4` -- those ids match `recovery.store/demo-data`,
  so it was safe to reuse rather than author from scratch), covering
  one full intake -> grading verify -> contamination screen ->
  material-grade certification -> impact-report publication lifecycle
  plus five distinct HARD-hold reasons, rendered deterministically --
  no invented numbers, no timestamps in the page content,
  byte-identical across reruns against the same seed (verify by
  diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [recovery.store :as store]
            [recovery.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :recovery-operator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: batch-1 clears a full lifecycle -- intake
  (auto-commit clean at phase 3, no capital risk), a grading verification
  (phase-gated -- not yet auto-eligible -- approved), a clean contamination
  screen (always escalates when clean -- approved), a material-grade
  certification (ALWAYS escalates -- `:actuation/certify-material-grade`
  is permanently high-stakes, never auto at any phase -- approved) and an
  impact-report publication (ALWAYS escalates --
  `:actuation/publish-impact-report`, same posture -- approved);
  batch-2 HARD-holds a grading verification with no official spec-basis
  for its (deliberately unregistered) jurisdiction ATL; batch-3 clears
  grading verification (approved) but then HARD-holds a material-grade
  certification whose measured contamination (8.0%) exceeds its own
  recorded maximum-allowed (5.0%); batch-4 HARD-holds a contamination
  screen that itself detects an unresolved contamination flag; batch-1
  then HARD-holds a second material-grade certification
  (`:already-certified`) and a second impact-report publication
  (`:already-published`). Every HARD hold never reaches a human.
  Returns the resulting store -- every field read by `render` below is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1-intake" {:op :batch/intake :subject "batch-1"
                               :patch {:id "batch-1" :batch-name "Sakura Community MRF Batch 4"}})

    (exec! actor "t1-grade" {:op :grading/verify :subject "batch-1"})
    (approve! actor "t1-grade")

    (exec! actor "t1-screen" {:op :contamination/screen :subject "batch-1"})
    (approve! actor "t1-screen")

    (exec! actor "t1-certify" {:op :actuation/certify-material-grade :subject "batch-1"})
    (approve! actor "t1-certify")

    (exec! actor "t1-report" {:op :actuation/publish-impact-report :subject "batch-1"})
    (approve! actor "t1-report")

    (exec! actor "t2-grade" {:op :grading/verify :subject "batch-2" :no-spec? true})

    (exec! actor "t3-grade" {:op :grading/verify :subject "batch-3"})
    (approve! actor "t3-grade")

    (exec! actor "t3-certify" {:op :actuation/certify-material-grade :subject "batch-3"})

    (exec! actor "t4-screen" {:op :contamination/screen :subject "batch-4"})

    (exec! actor "t1-certify-again" {:op :actuation/certify-material-grade :subject "batch-1"})

    (exec! actor "t1-report-again" {:op :actuation/publish-impact-report :subject "batch-1"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger batch-id]
  (last (filter #(= (:subject %) batch-id) ledger)))

(defn- status-cell [ledger batch-id]
  (let [f (last-fact-for ledger batch-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [{:keys [material-grade-certified? impact-report-published?]}]
  (cond
    (and material-grade-certified? impact-report-published?)
    "<span class=\"ok\">certified &amp; reported</span>"
    material-grade-certified?
    "<span class=\"warn\">certified, not yet reported</span>"
    impact-report-published?
    "<span class=\"warn\">reported, not certified</span>"
    :else "<span class=\"muted\">in intake / verification</span>"))

(defn- batch-row [ledger {:keys [id batch-name jurisdiction contamination-percentage
                                  contamination-max-allowed
                                  contamination-flag-unresolved?] :as b}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc batch-name) (esc jurisdiction)
          (esc (str contamination-percentage "% / max " contamination-max-allowed "%"))
          (if contamination-flag-unresolved?
            "<span class=\"critical\">unresolved</span>"
            "<span class=\"ok\">clear</span>")
          (lifecycle-cell b)
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- draft-row [prefix {:strs [record_id batch_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc batch_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops`, `recovery.governor`/`recovery.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:batch/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet</span></td></tr>"
   "        <tr><td><code>:grading/verify</code></td><td><span class=\"warn\">phase-3: human approval (not yet auto-eligible)</span> &middot; HARD hold on missing official spec-basis</td></tr>"
   "        <tr><td><code>:contamination/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean</span> &middot; an unresolved contamination flag is a HARD, un-overridable hold instead</td></tr>"
   "        <tr><td><code>:actuation/certify-material-grade</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; contamination % independently recomputed against the batch's own max-allowed ceiling &middot; double-certification refused</td></tr>"
   "        <tr><td><code>:actuation/publish-impact-report</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span> &middot; evidence checklist + unresolved contamination flag re-checked &middot; double-publication refused</td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-batches db)
        batch-rows (str/join "\n" (map (partial batch-row ledger) batches))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        cert-rows (str/join "\n" (map (partial draft-row "material-grade-certification")
                                      (store/certification-history db)))
        report-rows (str/join "\n" (map (partial draft-row "impact-report")
                                        (store/report-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-3830 &middot; materials recovery</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Materials recovery (ISIC 3830) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · material-grade certification / impact-report publication always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Materials-recovery batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>recovery.store</code> via <code>recovery.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Name</th><th>Jurisdiction</th><th>Contamination (max allowed)</th><th>Contamination flag</th><th>Certification/report status</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft material-grade-certification / impact-report records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the licensed materials-recovery operator's own act of certifying a real material grade or publishing a real impact report is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Batch</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     cert-rows (when (seq cert-rows) "\n")
     report-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Traceability Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Spec-basis, evidence completeness, contamination percentage ceiling, unresolved contamination flags, and double certification/publication are independently recomputed, never trusted from the proposal; a real material-grade certification or impact-report publication is always a human recovery operator's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Batch</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/certification-history db)) "material-grade-certification drafts,"
             (count (store/report-history db)) "impact-report drafts )")))
