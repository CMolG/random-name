# FCS Warehouse Fulfilment Assignment — Design Spec

**Date:** 2026-08-11
**Status:** Approved
**Scope:** Complete the FCS code assignment (Quarkus warehouse colocation management), answer
`QUESTIONS.md` and `CASE_STUDY.md`, and deliver the whole thing as a reviewable GitHub repository.

---

## 1. Goal and success criteria

Deliver the assignment *executed flawlessly* rather than expanded. Every stub implemented, every
stated constraint enforced, the bonus feature included, tests that genuinely pass, and both
written deliverables answered.

Success is judged on five things:

1. **Correctness** — every rule in `CODE_ASSIGNMENT.md` enforced, with the right HTTP semantics.
2. **Verifiability** — `./mvnw verify` is green in CI, from a clean clone, with one command.
3. **Reviewability** — a reviewer can run `git diff baseline..HEAD` and see exactly what was written.
4. **Judgment** — decisions are documented, including the ones deliberately *not* taken.
5. **Evidence of AI direction** — the process artifacts show a human steering the work, including
   correcting it.

### Explicit non-goals

Observability stacks, Testcontainers, native builds, Kubernetes manifests, transactional outbox,
and a second parallel implementation are all **out of scope**. The brief describes roughly four
hours of work for a senior engineer with an AI assistant. Padding it with unrequested
infrastructure reads as scope creep, not seniority. Where a production system would need more, that
is stated in an ADR instead of built.

---

## 2. Baseline analysis

### 2.1 What is stubbed

| Location | State |
|---|---|
| `LocationGateway.resolveByIdentifier` | throws `UnsupportedOperationException` |
| `WarehouseRepository` — `create`, `update`, `remove`, `findByBusinessUnitCode` | all four throw |
| `WarehouseResourceImpl` — `createANewWarehouseUnit`, `getAWarehouseUnitByID`, `archiveAWarehouseUnitByID`, `replaceTheCurrentActiveWarehouse` | all four throw |
| `CreateWarehouseUseCase`, `ReplaceWarehouseUseCase`, `ArchiveWarehouseUseCase` | bodies are `// TODO` + a bare store call |
| `StoreResource` | works, but syncs to the legacy system *before* commit |
| `CreateWarehouseUseCaseTest`, `ReplaceWarehouseUseCaseTest`, `ArchiveWarehouseUseCaseTest` | empty classes |
| `LocationGatewayTest`, half of `WarehouseEndpointIT` | fully commented out |

### 2.2 Defects present in the provided code

These are real and were verified, not assumed. The brief warns that some code is "implemented but
incomplete", so finding them is part of the task.

1. **`StoreResource.update` / `patch` sync the wrong object.** Both pass `updatedStore` — the
   deserialised request body, whose `id` is `null` — to `LegacyStoreManagerGateway` instead of the
   persisted `entity`. The legacy system receives an unidentifiable record.
2. **`StoreResource.patch` tests the wrong side.** It guards with `entity.name != null` and
   `entity.quantityProductsInStock != 0` when it means to test the *incoming* values. As written it
   can never null-out or zero a field, and it applies changes it was asked not to.
3. **`patch` rejects a null name with 422**, which contradicts PATCH semantics — a partial update
   is precisely a request that omits fields.
4. **`StoreResource.delete` never notifies the legacy system**, leaving it holding stores that no
   longer exist here.
5. **`pom.xml` sets conflicting compiler levels** — `maven.compiler.release=17` alongside
   `<source>11</source><target>11</target>` on `maven-compiler-plugin`. The build currently
   succeeds on `release 17`, but the contradiction is latent.
6. **`mvnw` is not executable** (mode `644`). Verified: `./mvnw` fails with
   `permission denied` locally, and would fail identically in CI.
7. **Root `README.md` links to `assignment/CODE_ASSIGNMENT.md`**; the file lives at
   `java-assignment/CODE_ASSIGNMENT.md`.
8. **`toWarehouseResponse` never sets the response `id`**, so the API can never return the
   identifier its own OpenAPI schema advertises.
9. **The generated `@NotNull` is inert.** `jakarta.validation-api:3.0.2` is on the compile
   classpath transitively, so `@NotNull` compiles — but `hibernate-validator` appears in the
   dependency tree only as `quarkus-hibernate-validator-spi:test`. There is no validation engine at
   runtime, so the contract's `required: true` is currently unenforced.

### 2.3 The seed data contradicts the rules

`Location` documents `maxCapacity` as "maximum capacity of the location summing all the warehouse
capacities". `ZWOLLE-001` is declared `maxNumberOfWarehouses=1, maxCapacity=40`, yet `import.sql`
seeds `MWH.001` there with **capacity 100** — 2.5× its location limit.

**Decision: grandfather it.** Validate on write only; never retro-validate existing rows. Editing
`import.sql` to make the implementation look correct would be quietly rewriting the fixtures, and a
reviewer may well be checking whether the candidate notices this. The consequence is a test
constraint: any test replacing `MWH.001` must use a capacity ≤ 40.

### 2.4 What the commented-out test proves

`WarehouseEndpointIT.testSimpleCheckingArchivingWarehouses` archives `/1` and then asserts
`ZWOLLE-001` is absent from the list. Row 1 of `import.sql` is `MWH.001 @ ZWOLLE-001`. Therefore:

- `{id}` in the OpenAPI paths is the **numeric database id**, not the business unit code.
- `listAllWarehousesUnits` must **exclude archived** warehouses.

---

## 3. Architecture

Follow the grain the codebase already establishes rather than imposing uniformity.

```
┌─────────────────────────────────────────────────────────────────┐
│ Inbound adapter                                                 │
│   WarehouseResourceImpl  implements  com.warehouse.api.         │
│                                      WarehouseResource          │
│                                      (generated from OpenAPI)   │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│ Domain                                                          │
│   CreateWarehouseUseCase   ─┐                                   │
│   ReplaceWarehouseUseCase   ├─→ WarehouseStore    (port)        │
│   ArchiveWarehouseUseCase  ─┘   LocationResolver  (port)        │
└───────────────┬─────────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────────┐
│ Outbound adapters                                               │
│   WarehouseRepository (Panache) ──→ PostgreSQL                  │
│   LocationGateway (in-memory reference data)                    │
└─────────────────────────────────────────────────────────────────┘
```

`Product` and `Store` stay as plain Panache CRUD — **deliberately, not by omission**. Question 1 of
`QUESTIONS.md` asks whether the mixed persistence strategies should be unified; the strongest answer
is "refactor by risk, not for symmetry", and leaving them alone lets that answer be demonstrated in
the diff rather than merely asserted. Wrapping ports and use cases around CRUD that has no business
rules would add roughly fifteen classes of ceremony for no behavioural gain.

---

## 4. Domain rules

Derived from `CODE_ASSIGNMENT.md` and the field comments on `Location`.

| Rule | Precise meaning |
|---|---|
| Business unit code uniqueness | No **active** warehouse may share the code. Archived rows keep theirs — that is the history trail the brief describes. |
| Location validity | `LocationResolver.resolveByIdentifier` returns non-null. |
| Creation feasibility | `count(active at location) < location.maxNumberOfWarehouses` |
| Capacity | `sum(capacity of active at location) + new.capacity ≤ location.maxCapacity` — a **sum across the location**, not a per-warehouse ceiling. |
| Stock fits capacity | `new.stock ≤ new.capacity` |
| Replace — accommodation | `new.capacity ≥ previous.stock` |
| Replace — stock match | `new.stock == previous.stock` |

### Operation semantics

- **Create** — validate, persist, return **201** via `@ResponseStatus(201)`. The generated interface
  returns `Warehouse`, not `Response`, so the annotation is the mechanism; a `ContainerResponseFilter`
  would be a heavier way to reach the same result.
- **Get by id** — numeric id, active only, else **404**.
- **List** — active only.
- **Archive** — soft state change, `archivedAt = now()`. Archiving an already-archived or unknown
  warehouse is **404**, not a silent success. Returns **204** (the generated method returns `void`).
- **Replace** — archive the previous active warehouse for the business unit code and create the
  successor with the same code, **in a single transaction**. A partial replace would strand a
  business unit code with no active warehouse, which is exactly the history-integrity failure
  Scenario 5 of the case study asks about.

### Decisions on ambiguities

| # | Ambiguity | Decision |
|---|---|---|
| 1 | Seed data violates capacity rules | Grandfather; validate writes only |
| 2 | `{id}` — db id or business unit code? | Numeric db id, per the commented-out IT. Requires adding `id` to the domain model and populating it in the response. |
| 3 | Archive vs delete | Soft archive; archived rows stay queryable but are excluded from all read paths |
| 4 | `WarehouseStore.remove` | Implemented as archival, not hard delete. It is a leftover from a hard-delete model; adding a `DELETE` nothing calls would be worse than repurposing it honestly. |

---

## 5. Contract-first API and validation

**The OpenAPI YAML is the source of truth.** `quarkus-openapi-generator-server` stays — it runs
inside the Quarkus codegen lifecycle and its version is aligned with the platform, which is a more
solid arrangement than bolting on a standalone Maven plugin.

### How constraints reach the code

`quarkus-openapi-generator-server` wraps **Apicurio** codegen (not `openapi-generator`), and Apicurio
models are produced by `jsonschema2pojo`, which never emits bean-validation annotations. Apicurio's
own mechanism is the vendor extension **`x-codegen-annotations`**, confirmed present in
`apicurio-codegen 1.1.1.Final` (`CodegenExtensions.ANNOTATIONS`, read by
`OpenApi2CodegenVisitor.visitSchema`) alongside `x-codegen-type`, `x-codegen-extendsClass`,
`x-codegen-returnType` and others.

So: declare constraints in `warehouse-openapi.yaml` via `x-codegen-annotations`, so `@NotNull`,
`@Size`, `@Positive` land on the generated bean fields; add **`quarkus-hibernate-validator`** so they
are actually enforced; add `@Valid` on the interface parameters.

> **Rejected alternative — and a correction on record.** The first recommendation in this design was
> to replace the provided generator with `openapi-generator-maven-plugin` for its
> `useBeanValidation` flag. That was wrong, and was corrected during review: leaving the Quarkus
> lifecycle to gain a flag that `x-codegen-annotations` already covers trades architectural
> coherence for nothing. Recorded in ADR-0004.
>
> A related factual point, verified by extracting the deployment jars of all twenty published
> versions: `use-bean-validation` exists on the **client** extension
> (`quarkus-openapi-generator`, which wraps `openapi-generator`) but **not** on the **server**
> extension, whose entire config surface across 2.4.1 → 2.9.0 is `base-package`, `spec`,
> `input-base-dir`, `reactive`. Setting it on the server artifact would silently do nothing.

### Mapping

Hand-written, in a single small mapper. MapStruct was evaluated against the stated criterion — adopt
only if it reduces code substantially — and **rejected on the numbers**: four mappings, ~37 hand-written
lines versus ~30 with MapStruct plus build configuration and an extra extension. Roughly break-even.
The genuine argument for it (`unmappedTargetPolicy = ERROR` catching the dropped-`id` defect at
compile time) is a correctness argument, not a line-count one, and does not meet the criterion.
Recorded in ADR-0005. Lombok is likewise not introduced: the entities are public-field POJOs and
Quarkus + Lombok + annotation processors is friction for no gain across four small classes.

### Error model

`WarehouseDomainException` as the base type, with `WarehouseNotFoundException` → **404**,
`DuplicateBusinessUnitCodeException` → **409**, and capacity / location / replacement violations →
**400**. A single `ExceptionMapper` translates them, emitting the same JSON error shape the existing
`ErrorMapper` already produces so the API stays internally consistent.

---

## 6. Store legacy sync

`StoreChangedEvent` — an immutable record carrying `id`, `name`, `quantityProductsInStock` and a
`CREATE`/`UPDATE`/`DELETE` discriminator — is fired from inside the `@Transactional` method and
observed with `@Observes(during = TransactionPhase.AFTER_SUCCESS)`, which invokes
`LegacyStoreManagerGateway`.

**The event carries a snapshot, not the entity.** After commit the persistence context is closed, so
handing the observer a managed entity invites detached-access failures. Building the snapshot from
the *persisted* `entity` also fixes defect 2.2.1 by construction — the legacy system finally receives
a record with an id.

A rollback delivers nothing, which is the entire point of the task and is directly testable with an
alternative observer that records invocations.

**Known limitation, stated rather than hidden:** `AFTER_SUCCESS` gives *at-most-once* delivery. If
the legacy write fails after commit, the two systems diverge silently. The production answer is a
transactional outbox with a retrying publisher; that is deliberately out of scope here and is
recorded in ADR-0003 instead of built.

Defects 2.2.2, 2.2.3 and 2.2.4 are fixed as part of this work, since correct propagation is
meaningless if the payload or the trigger is wrong.

---

## 7. Bonus — fulfilment associations

`FulfilmentAssociation(store, product, warehouse)` with a database unique constraint on the triple.

| Constraint | Rule |
|---|---|
| Per product, per store | ≤ 2 distinct warehouses |
| Per store | ≤ 3 distinct warehouses |
| Per warehouse | ≤ 5 distinct products |

Endpoints: `POST /fulfilment`, `GET /fulfilment?storeId=`, `DELETE /fulfilment/{id}`. A lean vertical
slice consistent with the warehouse idiom, without re-deriving the full hexagon for three rules.

**Known limitation:** the count-based rules are check-then-act and therefore racy under concurrent
requests. The unique constraint prevents duplicate triples but not a simultaneous pair of inserts
that each individually satisfy a count. Serialisable isolation or a locking read would close it.
Stated in ADR-0006 rather than papered over.

---

## 8. Testing strategy

TDD on the domain, committed red → green → refactor so the history is itself the evidence.

| Layer | Tooling | Covers |
|---|---|---|
| Domain unit | plain JUnit 5, no Quarkus, `InMemoryWarehouseStore` + `StaticLocationResolver` fakes | every rule in §4, both happy and violation paths |
| REST | `@QuarkusTest` + RestAssured | status codes, error shape, validation rejection, post-commit sync ordering |
| Integration | `@QuarkusIntegrationTest` (`WarehouseEndpointIT`, uncommented) | packaged application against a real database |

### Mutation testing

**PIT 1.19.1 + `pitest-junit5-plugin` 1.2.2**, scoped to `warehouses.domain.*` and `location.*`,
driven by the plain JUnit tests.

Scoping is a deliberate engineering decision, not an oversight: PIT rewrites bytecode, and Quarkus
also rewrites bytecode and uses its own classloader, so pointing PIT at `@QuarkusTest` classes is
unreliable. The domain layer is also where the business rules — and therefore the risk — actually
live. Recorded in ADR-0007.

Runs in a `mutation` Maven profile and a separate CI job, so the ordinary build stays fast. Initial
threshold 85%. **Every surviving mutant is triaged**: either the test is strengthened until it kills
the mutant, or the test is deleted as worthless. Discarding tests that assert nothing is the explicit
purpose of adopting mutation testing here.

---

## 9. Repository, CI and process

### Layout

```
/
├─ README.md                  rewritten as the submission front door
├─ .gitignore                 new
├─ .github/workflows/ci.yml   new
├─ docs/
│  ├─ adr/                    numbered decision records
│  ├─ AI_COLLABORATION.md     how the work was directed
│  └─ superpowers/{specs,plans}/
├─ case-study/CASE_STUDY.md   answered in place
├─ java-assignment/
│  ├─ CODE_ASSIGNMENT.md      untouched
│  ├─ QUESTIONS.md            answered in place
│  └─ src/…
├─ .agents/skills/            committed — evidence of the configured AI workflow
└─ skills-lock.json
```

`CODE_ASSIGNMENT.md`, `BRIEFING.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` and `LICENSE` stay
byte-identical to the originals.

### History

The first commit is the untouched baseline, tagged `baseline`, so the complete delta is
`git diff baseline..HEAD`. Reviewability was the objective: of the comparable public submissions
surveyed, none preserved a clean diff point, which makes their work substantially harder to assess.

Work then proceeds one GitHub issue per assignment task, following the repository's own
`CONTRIBUTING.md` convention — a branch per issue, Conventional Commits referencing it, a pull
request, and the issue answered and closed on merge.

### CI

`ubuntu-latest`, Temurin 21, cached Maven.

- **build** — `./mvnw -B verify`. Quarkus Dev Services provides PostgreSQL via Docker, which GitHub
  Actions supplies.
- **mutation** — `./mvnw -Pmutation …` publishing the PIT HTML report as an artifact.

`mvnw` must be made executable (defect 2.2.6) before CI can work at all.

**No JaCoCo.** Mutation score strictly dominates line coverage as a quality signal; publishing both
invites a reviewer to anchor on the weaker number. One strong metric, defended.

### Evidence of AI direction

`docs/AI_COLLABORATION.md` records the workflow — brainstorm → spec → plan → TDD → mutation triage —
and, candidly, **where the AI was wrong and how it was caught**. Two entries already exist from the
design session: recommending replacement of the provided OpenAPI generator (corrected to
`x-codegen-annotations` within the Quarkus lifecycle), and reporting `quarkus-mapstruct` as
non-existent when the Maven query was simply malformed. A record of the machine being corrected is
stronger evidence of competent direction than a clean narrative would be.

---

## 10. Written deliverables

`QUESTIONS.md` and `CASE_STUDY.md` are answered in place, inside the existing template blocks, in
English.

**Answers argue from reasoning and from this codebase — they make no personal or biographical
claims.** Where experience would normally be cited, the answer instead reasons from the concrete
situation in front of it. This is a deliberate constraint agreed during design.

- **Q1 — persistence strategies.** The codebase genuinely mixes three: active-record Panache
  (`Store`), Panache repository (`ProductRepository`), and a hexagonal port (`WarehouseStore`).
  Position: refactor by risk, not for symmetry — demonstrated in the diff. Includes the concrete
  recommendation that `drop-and-create` + `import.sql` must become versioned migrations before
  production.
- **Q2 — contract-first versus code-first.** Answered first-hand from §5: the generator's actual
  capabilities, driving constraints from the YAML, and the discovery that the shipped `@NotNull` was
  inert.
- **Q3 — test prioritisation.** Answered from §8: the pyramid, why the domain layer carries the
  business-rule coverage, and why mutation score was chosen over line coverage.

`CASE_STUDY.md` answers all five scenarios with reasoning plus the explicit discovery questions the
brief asks for. Scenario 5 — cost control in warehouse replacement — is answered directly from the
implementation in this repository: archive rather than delete, business unit code reuse, effective
dating, and the transactional atomicity of replace.

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| Local JDK default is 25; Quarkus 3.13 targets 17/21 | Build with JDK 21 (verified: `BUILD SUCCESS`, `release 17`). CI pins Temurin 21. |
| `x-codegen-annotations` placement not yet exercised end to end | Verified present in the codegen library and read by `visitSchema`; confirm empirically with a real build as the first implementation step, before depending on it. |
| `@QuarkusIntegrationTest` needs a packaged app plus a database | Runs under `verify` with Dev Services; confirm in CI early rather than at the end. |
| Mutation threshold too aggressive for the time budget | Start at 85%, triage survivors, adjust the threshold consciously and record the final number. |
