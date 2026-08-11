# How this assignment was built with an AI assistant

The brief says this takes "around 4 hours by a senior 🤖". That framing is an invitation to show
*how* you work with an assistant, not just what it produced. This document is the honest record.

## The workflow

```
brainstorm  →  spec  →  adversarial review  →  plan  →  TDD  →  mutation triage
   │            │              │                 │        │            │
   │            │              │                 │        │            └─ delete tests that
   │            │              │                 │        │               assert nothing
   │            │              │                 │        └─ red → green → refactor,
   │            │              │                 │           committed in that order
   │            │              │                 └─ ordered steps, one issue each
   │            │              └─ fresh-context reviewer, 3 rounds
   │            └─ docs/superpowers/specs/
   └─ requirements and constraints settled before any code
```

No code was written until the design was settled and reviewed. That ordering is the point: the
expensive defects in this assignment are design defects, and every one of them was cheaper to find
in a document than in a test run.

## What the review rounds actually caught

Three rounds of adversarial spec review, each by a reviewer with no memory of the conversation that
produced the spec. They were not a formality:

| Round | Found |
|---|---|
| 1 | Five issues, including that the mechanism proposed for contract-driven validation does not work |
| 2 | The integration test had no database — which led to discovering it could not run *at all* |
| 3 | A self-contradiction in the field-validation rules, plus `LocationGateway` not being a CDI bean and `getAll()` silently returning archived rows |

Round 2 is the one worth reading twice. The reviewer flagged a missing database; investigating that
by *running the build* rather than reasoning about it uncovered a harder problem underneath —
Quarkus Dev Services could not start a database on the development machine at all, because Docker
Desktop 29.2.0 answers `/info` with HTTP 400 to the `docker-java` client bundled with Quarkus
3.13.3. Every Testcontainers strategy fails and the build aborts. Nothing in the assignment hints at
this, and it would have surfaced as an inexplicable failure on the first red test of a TDD cycle.

## Where the AI was wrong

This is the section that matters. An assistant that is never wrong is an assistant nobody is
checking.

**1. It recommended replacing the provided OpenAPI generator.** The proposal was to swap
`quarkus-openapi-generator-server` for the standalone `openapi-generator-maven-plugin` to obtain a
`useBeanValidation` flag. I rejected it: leaving the Quarkus codegen lifecycle for a build-time
plugin trades architectural coherence for a flag, and the provided extension has a documented
mechanism of its own.

**2. It then asserted that `x-codegen-annotations` would put constraints on the generated bean
fields — without having run it.** The annotation extension is real and is documented; the inference
that it applies at property level was not tested. I told it to test the premise before I would
accept any conclusion built on it. The experiment disproved the claim: property-level annotations
are silently ignored, schema-level ones land on the class rather than the fields, and no
configuration of the flag in any published version changes it.

That correction is the most valuable thing in this repository. The resulting configuration matrix —
four flag spellings across two extension versions, with the generated output captured each time — is
now the evidence behind the answer to Question 2, which asks precisely about the trade-offs of
generating code from an API contract. A first-hand measurement replaced an opinion.

**3. It reported a Maven artifact as non-existent when its own query was malformed.** It searched
Maven Central for `io.quarkiverse.mapstruct:quarkus-mapstruct`, got zero results, and concluded the
artifact did not exist. The artifact exists at version 1.1.0; the search API call was wrong. A tool
returning nothing is not evidence of absence.

The pattern in all three: the assistant is fast and thorough at gathering evidence, and prone to
over-trusting a chain of inference that it has not executed. The correction in each case was to
demand the experiment.

## What mutation testing caught that a green build did not

Scope first, since the number is meaningless without it: **44 mutants across the four rule-bearing
domain classes** — `CreateWarehouseUseCase` (26), `ReplaceWarehouseUseCase` (10), `LocationGateway`
(5), `ArchiveWarehouseUseCase` (3). Adapters, entities, generated beans and container-driven tests
are excluded per ADR-0007, with PIT's default mutator set.

The domain suite was green, and every rule had a test. PIT then reported **84%** — 44 mutants, 7
alive. Each survivor is a sentence the tests could not finish.

| Surviving mutant | What it proved was untested |
|---|---|
| `stock < 0` → `stock <= 0` | Nothing ever created a warehouse with **zero stock** — the state every new warehouse starts in |
| `stock > capacity` → `>=` | Nothing ever created a **full** warehouse, so "stock may equal capacity" was an assumption, not a rule |
| capacity `< previous.stock` → `<=` | Same boundary on replacement: a successor sized exactly to the stock it inherits |
| location filter → always `true` | Every location test used **one** location, so "limits are per location" was never actually asserted |
| `maxCapacity - used` → `+` | The remaining-capacity figure in the error message was never read by anyone |
| `requireText(location)` removed | A blank location still failed — via the resolver, with the message "location    does not exist". Two different client mistakes collapsed into one useless sentence |
| `warehouseStore.update(...)` removed | The in-memory double holds warehouses by reference, so archiving was visible whether or not anything was persisted. Against a database it would not be |

Five were missing boundary cases, one was a message nobody checked, and the last was a defect in the
**test double** rather than in the tests: holding objects by reference made persistence unobservable,
so the double now counts the calls the database would have needed. After the triage: **44 mutants,
44 killed, no survivors — across those four classes.**

The fulfilment limits sit outside that scope, because `FulfilmentService` needs a container to
test. They were checked by a one-off manual mutation instead — flipping each `>=` to `>` and
confirming exactly the three boundary tests failed — which is not something CI repeats. Putting the
counting behind a port, as the warehouse use cases do, is what would bring it into the automated
run; that is a design change rather than a configuration one, and it was not made.

No test was deleted. That was the outcome worth reporting either way — the technique's payoff is
usually a test that asserts nothing, and here it was instead six rules that were only half-asserted
plus a double that lied. Line coverage was 99% before the triage and 99% after it; it moved by
nothing while the suite got materially stronger, which is the whole argument for measuring mutants
instead of lines.

## Why the skill files are committed

`.agents/skills/` and `skills-lock.json` are committed **on purpose**, not by accident.

They are the actual configuration that drove this work — the brainstorming, planning, TDD and
debugging skills, pinned by content hash in `skills-lock.json`. Committing them means a reviewer can
clone this repository and inspect the exact working stack that produced the result, without fetching
anything else or taking my word for what the process was.

`.claude/skills/` contains relative symlinks into `.agents/skills/`; git stores them as symlinks
(mode `120000`) and they resolve correctly after a clone.

These files are third-party, vendored unmodified from [obra/superpowers](https://github.com/obra/superpowers)
(MIT licensed). They are not my work and are included only as reproducibility evidence.

## Verifying any of this

The first commit is the assignment exactly as provided, tagged `baseline`:

```sh
git diff baseline..HEAD          # everything written on top of the provided code
git log --oneline baseline..HEAD # the order it was written in
```

Decision records live in `docs/adr/`, the design in `docs/superpowers/specs/`, and the
implementation plan in `docs/superpowers/plans/`. Where a decision was reversed, the record says so
and says why, rather than presenting the final answer as if it were the first one.
