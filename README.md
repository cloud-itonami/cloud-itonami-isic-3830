# cloud-itonami-isic-3830

Open Business Blueprint for **ISIC Rev.5 3830**: materials recovery.

This repository publishes a materials-recovery actor -- batch intake,
grading verification, contamination-flag screening, material-grade
certification and impact-report publication -- as an OSS business
that any qualified recycling/recovery operator can fork, deploy, run,
improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030)) --
the SECOND infrastructure/circular-economy vertical in this fleet
(after `3600`'s water-safety operations; distinct from `3600`'s and
`6190`'s utility-service domains, and from `3030`'s manufacturing
domain). Here it is **Recovery Advisor ⊣ Traceability Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a batch-
> intake summary, normalizing records, and checking whether a batch's
> own measured contamination percentage actually stays within its own
> recorded maximum-allowed ceiling -- but it has **no notion of which
> jurisdiction's materials-recovery grading standard is official, no
> license to certify a real material grade or publish a real impact/
> ESG report, and no way to know on its own whether a contamination
> flag against the batch has actually stayed unresolved**. Letting it
> certify a grade or publish a report directly invites fabricated
> grading-standard citations, an over-contaminated batch being
> certified as a clean grade, and an unresolved contamination flag
> being quietly reported as resolved -- and liability, and consumer/
> ESG-fraud risk, for whoever runs it. This project seals the Recovery
> Advisor into a single node and wraps it with an independent
> **Traceability Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers batch intake through grading verification,
contamination-flag screening, material-grade certification and
impact-report publication. It does **not**, by itself, hold any
license required to operate a materials-recovery facility in a given
jurisdiction, and it does not claim to. It also does **not** model a
real scale/scanner/MRF (materials recovery facility) control system,
a real buyer-matching/logistics engine, or route optimization itself
-- no sensor-protocol-specific ingestion pipeline (see `recovery.
facts`'s own docstring for the honest simplification this makes: a
starting catalog of grading-standard authorities, not a survey of
every jurisdiction's materials-recovery-standard variant). Whoever
deploys and operates a live instance (a certified recovery operator)
supplies any jurisdiction-specific license, the real sorting/grading
engineering and the real scale/scanner/logistics integrations, and
bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator does
not have to build the compliance layer from scratch for every new
market.

### Actuation

**Certifying a real material grade or publishing a real impact report
is never autonomous, at any phase, by construction.** Two independent
layers enforce this (`recovery.governor`'s `:actuation/certify-
material-grade`/`:actuation/publish-impact-report` high-stakes gate
and `recovery.phase`'s phase table, which never puts `:actuation/
certify-material-grade`/`:actuation/publish-impact-report` in any
phase's `:auto` set) -- see `recovery.phase`'s docstring and `test/
recovery/phase_test.clj`'s `certify-material-grade-never-auto-at-any-
phase`/`publish-impact-report-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human recovery operator is always the
one who actually certifies a grade or publishes an impact report.
Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/
`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`/
`8521`/`6619`/`3600`/`6190`/`3030`, this actor has TWO actuation
events, both POSITIVE (issuing/finalizing a real record), matching the
majority pattern in this fleet (`3600`/`6190` are the fleet's two
NEGATIVE-actuation exceptions).

## The core contract

```
batch intake + jurisdiction facts (recovery.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Recovery     │ ─────────────▶ │ Traceability                  │  (independent system)
   │ Advisor      │  + citations    │ Governor:                    │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ contamination-
                           record + ledger  escalate ─▶ human   exceeds-maximum
                                             (ALWAYS for         (ceiling) ·
                                              :actuation/certify-        contamination-
                                              material-grade /            flag-unresolved
                                              :actuation/publish-          (unconditional) ·
                                              impact-report)               already-certified/
                                                                            -published
```

**The Recovery Advisor never certifies a material grade or publishes
an impact report the Traceability Governor would reject, and never
does so without a human sign-off.** Hard violations (fabricated
grading-standard requirements; unsupported evidence; a contamination
percentage over its own ceiling; an unresolved contamination flag; a
double certification or publication) force **hold** and *cannot* be
approved past; a clean certification/publication proposal still always
routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a sorting and handling robot
performs grading, baling and loadout of recovered materials under the
actor, gated by the independent **Traceability Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions (such as operating near heavy bales, baling equipment or
conveyors) require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Traceability Governor, material-grade-certification + impact-report draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`3830`). This vertical's batch records are practice-specific rather
than a shared cross-operator data contract, so `recovery.*` runs on
the generic robotics/telemetry/forms/dmn/bpmn/audit-ledger/
optimization stack only -- no bespoke domain capability lib to
reference at all.

## Layout

| File | Role |
|---|---|
| `src/recovery/store.cljk` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate material-grade-certification/impact-report history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded batch, and the double-actuation guards check dedicated `:material-grade-certified?`/`:impact-report-published?` booleans rather than a `:status` value |
| `src/recovery/registry.cljk` | Material-grade-certification + impact-report draft records, plus `contamination-percentage-exceeds-maximum?` -- the FOURTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card` established the first three) |
| `src/recovery/facts.cljk` | Per-jurisdiction materials-recovery grading-standard catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/recovery/recoveryadvisor.cljk` | **Recovery Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/verification/contamination-screening/material-grade-certification/impact-report proposals |
| `src/recovery/governor.cljk` | **Traceability Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · contamination-exceeds-maximum, pure ground-truth ceiling recompute · contamination-flag-unresolved, unconditional evaluation, the TWENTY-EIGHTH grounding of this discipline and FIRST specifically for a contamination-flag concept) + already-certified/already-published guards + 1 soft (confidence/actuation gate) |
| `src/recovery/phase.cljk` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both material-grade certification and impact-report publication always human; batch intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/recovery/operation.cljk` | **OperationActor** -- langgraph-clj StateGraph |
| `src/recovery/sim.cljk` | demo driver |
| `test/recovery/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers batch intake through grading verification,
contamination-flag screening, material-grade certification and
impact-report publication -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Batch intake + per-jurisdiction materials-recovery grading checklisting, HARD-gated on an official spec-basis citation (`:batch/intake`/`:grading/verify`) | Real scale/scanner/MRF control-system integration, real buyer-matching/logistics/route-optimization engine (see `recovery.facts`'s docstring) |
| Contamination-flag screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:contamination/screen`) | Real pickup-route planning itself |
| Material-grade certification, HARD-gated on full evidence and contamination-ceiling sufficiency, plus a double-certification guard (`:actuation/certify-material-grade`) | Ongoing collection/sorting workflows themselves |
| Impact-report publication, HARD-gated on full evidence and a double-publication guard (`:actuation/publish-impact-report`) | |
| Immutable audit ledger for every intake/verification/screening/certification/publication decision | |

Extending coverage is additive: add the next gate (e.g. a buyer-
specification-match check) as its own governed op with its own HARD
checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`recovery.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `recovery.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `recovery.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `Recovery Advisor` + `Traceability Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the forty-
three prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
