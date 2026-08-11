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
| `LocationGateway.resolveByIdentifier` | throws `UnsupportedOperationException` — and the class carries **no bean-defining annotation**, so injecting `LocationResolver` fails at build time until `@ApplicationScoped` is added |
| `WarehouseRepository.getAll` | **not stubbed, but wrong for the target behaviour**: it returns `listAll()`, archived rows included. §4 requires active-only, so this working method changes too — easy to skip precisely because it already compiles and runs |
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
2. **`StoreResource.patch` corrupts stock, and cannot set it.** `Store.quantityProductsInStock` is
   a primitive `int`, so an omitted field deserialises to `0` — indistinguishable from an explicit
   zero. The guard reads `entity.quantityProductsInStock != 0`, i.e. the *stored* value rather than
   the incoming one. Actual behaviour: when the stored quantity is non-zero, a PATCH that omits the
   field **wipes it to 0**; when the stored quantity is 0, the incoming value is **ignored and can
   never be set**. The `entity.name != null` guard is wrong the same way, though harmless in
   practice since a loaded entity always has a name.
3. **`patch` rejects a null name with 422**, which contradicts PATCH semantics — a partial update
   is precisely a request that omits fields.
4. **`StoreResource.delete` never notifies the legacy system**, leaving an orphan record in the
   legacy register. See §6 — this is recorded as a scope decision, not fixed.
5. **`pom.xml` sets conflicting compiler levels** — `maven.compiler.release=17` alongside
   `<source>11</source><target>11</target>` on `maven-compiler-plugin`. The build currently
   succeeds on `release 17`, but the contradiction is latent. **Fixed**: the stale `source`/`target`
   pair is removed, leaving `release` authoritative. The pom is being edited anyway for Failsafe,
   hibernate-validator and PIT, so leaving a known contradiction in a file we are already touching
   would be hard to defend.
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
10. **`WarehouseResourceImpl` re-declares `@NotNull` on overriding parameters** (lines 22 and 41).
    Jakarta Validation forbids declaring parameter constraints on an overriding method, so this
    becomes a `ConstraintDeclarationException` the moment a validation engine exists. A
    codebase-wide grep confirms these are the **only** two parameter constraints present, both in a
    class this work rewrites — so the hazard is contained, but it must be known before adding
    `quarkus-hibernate-validator`.
11. **`WarehouseEndpointIT` never executes.** `maven-failsafe-plugin` is declared only inside the
    `native` profile, and Surefire's default includes do not match `*IT`. `./mvnw verify` therefore
    passes **without ever running** the one test that exercises the packaged application.

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

### Field-level rules

Because the generator cannot express these (§5), they are enforced in the use cases and are listed
here explicitly so they are testable rather than left to an implementer's discretion. None may
surface as an NPE.

| Field | Rule | Exception | Status |
|---|---|---|---|
| `businessUnitCode` | non-null, non-blank after trim | `InvalidWarehouseDataException` | 400 |
| `location` | non-null, non-blank after trim (existence is then checked by `LocationResolver`) | `InvalidWarehouseDataException` | 400 |
| `capacity` | non-null, `> 0` — a warehouse with no capacity cannot hold the stock the same request declares | `InvalidWarehouseDataException` | 400 |
| `stock` | non-null, `>= 0` | `InvalidWarehouseDataException` | 400 |
| `id` on create | must be absent | `ClientSuppliedIdException` | **422** |

The client-supplied-id case keeps **422** for parity with `ProductResource` and `StoreResource`,
which both already return 422 for exactly this. Consistency across the three resources is worth more
than collapsing everything into one status, and the assignment's Question 1 is about precisely this
kind of cross-resource coherence. It therefore needs its **own** exception type, and **422 is added
to `POST /warehouse` in the YAML alongside 409** — the contract changes first, per §5.

No maximum lengths are enforced. The contract declares none, the database columns declare none, and
inventing bounds the contract does not describe would be the same mistake as writing constraints the
generator cannot honour.

### Operation semantics

- **Create** — validate, set `createdAt = now()`, persist, return **201** via `@ResponseStatus(201)`.
  The generated interface returns `Warehouse`, not `Response`, so the annotation is the mechanism;
  a `ContainerResponseFilter` would be a heavier way to reach the same result.
- **Get by id** — numeric id, active only, else **404**.
- **List** — active only.
- **Archive** — soft state change, `archivedAt = now()`. Archiving an already-archived or unknown
  warehouse is **404**, not a silent success. Returns **204** (the generated method returns `void`).
- **Replace** — archive the previous active warehouse for the business unit code, then create the
  successor with the same code, **in a single transaction**. A partial replace would strand a
  business unit code with no active warehouse, which is exactly the history-integrity failure
  Scenario 5 of the case study asks about.

  **Ordering is load-bearing.** The successor is validated with the full creation rule set
  (§4 table) evaluated *after* the predecessor is archived. This is the only self-consistent
  reading: were the predecessor still active, the successor would collide with it on both business
  unit code uniqueness and the location warehouse count, and no replacement could ever succeed.
  It is also why replacing `MWH.001` requires a capacity ≤ 40 — once the over-capacity predecessor
  is archived, the location's used capacity drops to 0 and the successor must fit within
  `ZWOLLE-001`'s declared 40.

### Decisions on ambiguities

| # | Ambiguity | Decision |
|---|---|---|
| 1 | Seed data violates capacity rules | Grandfather; validate writes only |
| 2 | `{id}` — db id or business unit code? | Numeric db id, per the commented-out IT. Requires adding `id` to the domain model and populating it in the response. |
| 3 | Non-numeric `{id}` (contract types it `string`, the column is `Long`) | **404**, not 400. A malformed id is indistinguishable to a client from an id that does not exist, and 404 avoids leaking the storage type. |
| 4 | Archive vs delete | Soft archive; archived rows stay queryable but are excluded from all read paths |
| 5 | `WarehouseStore.remove` | Implemented as archival, not hard delete. It is a leftover from a hard-delete model; adding a `DELETE` nothing calls would be worse than repurposing it honestly. |
| 6 | Which store method does `ArchiveWarehouseUseCase` call? | `update`. The provided skeleton already calls `update`, archival is a field mutation on an existing row, and `remove` is its alias per decision 5. One archival path, not two. |
| 7 | Who sets `createdAt`? | The create use case, at validation time, inside the transaction. Not the database, so the value is deterministic and assertable in tests. |
| 8 | `WarehouseStore` has no lookup by numeric id, which decision 2 requires for `GET`/`DELETE /warehouse/{id}` | The **port gains `findById(Long)`**. `WarehouseResourceImpl` currently injects the concrete `WarehouseRepository`; reaching through it to `PanacheRepository.findById` would make the adapter depend on the implementation and quietly defeat the hexagon the rest of §3 maintains. The impl's field type changes to `WarehouseStore`. |
| 9 | `businessUnitCode` appears in **both** the replacement path and the request body | **The path is authoritative, and a mismatch is 400.** Silently preferring one would hide a client bug in an operation whose entire purpose is to preserve a specific business unit code. An absent body value is taken from the path. |
| 10 | The API bean's `id` is `String`; the column is `Long` | `String.valueOf` outbound; inbound parse failures fall into decision 3 (**404**, not a 500). |
| 11 | Must a fulfilment association reference existing entities? | Yes — the warehouse must exist and be **active**, and the store and product must exist. A **404** naming the missing entity. Associating stock with an archived warehouse would silently corrupt exactly the history trail §2.3 and Scenario 5 are about. |

---

## 5. Contract-first API and validation

**The OpenAPI YAML is the source of truth.** `quarkus-openapi-generator-server` stays — it runs
inside the Quarkus codegen lifecycle and its version is aligned with the platform, which is a more
solid arrangement than bolting on a standalone Maven plugin.

### How constraints reach the code — an empirical finding

`quarkus-openapi-generator-server` wraps **Apicurio** codegen, not `openapi-generator`, and Apicurio
models are produced by `jsonschema2pojo`. **Field-level bean validation is not reachable through
this generator.** This was established by experiment on a scratch copy of the module, not inferred:

| Configuration tried | Extension | Annotations on `businessUnitCode` | `@Valid` on request body |
|---|---|---|---|
| `required` / `minLength` / `maxLength` / `minimum` in the YAML | 2.4.7 | none — `@JsonProperty` and a `(Required)` javadoc | no |
| `quarkus.openapi.generator.use-bean-validation=true` | 2.4.7 | none | no |
| `…server.use-bean-validation` and `…codegen.spec.*.use-bean-validation` | 2.4.7 | none — output byte-identical | no |
| `quarkus.openapi.generator.server.use=openapitools` | 2.4.7 | none | no |
| all of the above | **2.9.0** | none | no — **build fails** |
| property-level `x-codegen-annotations` | 2.4.7 | **silently ignored** | no |
| schema-level `x-codegen-annotations` | 2.4.7 | applied **class-level** (`@NotNull public class Warehouse`), inert for fields | no |

Three consequences worth recording:

- **The flag does not exist on this artifact.** Unknown build-time codegen properties are ignored
  without warning, so the misconfiguration is silent. Extracting the deployment jars of all twenty
  published server versions confirms the entire config surface across 2.4.1 → 2.9.0 is
  `base-package`, `spec`, `input-base-dir`, `reactive`. `use-bean-validation` belongs to the
  **client** extension (`quarkus-openapi-generator`), which wraps `openapi-generator`.
- **`@Valid` is never generated**, so field constraints would be inert even if they appeared. It
  cannot be added in our implementation either: Jakarta Validation prohibits adding `@Valid` or
  parameter constraints on an overriding method (see defect 2.2.10).
- **Upgrading is not free.** Extension 2.9.0 emits MicroProfile OpenAPI annotations and fails to
  compile with `package org.eclipse.microprofile.openapi.annotations does not exist`, because
  `quarkus-smallrye-openapi` is not a dependency of this project. "Revisit after a version bump"
  would be inaccurate advice; the capability lives in a different extension entirely.

`x-codegen-annotations` syntax note, for the record: the annotation string must omit the leading
`@`, which Apicurio prepends. Including it emits `@@…` and fails to compile.

### Decision

Keep the provided generator and the contract-first approach. Then:

1. Add **`quarkus-hibernate-validator`**. This is not decorative — it gives the generated interface's
   `@NotNull` on the request body an engine for the first time, which is precisely the fix for
   defect 2.2.9. Null bodies are then rejected as the contract has always promised.
2. **Remove the redeclared `@NotNull`** from `WarehouseResourceImpl` (defect 2.2.10). Constraints are
   inherited from the interface; redeclaring them throws at startup.
3. **Field-level rules live in the use cases**, alongside the business rules they are inseparable
   from. A blank business unit code and a duplicate business unit code are the same kind of
   rejection to a caller; splitting them across two layers would buy nothing.
4. **No `minLength` / `maxLength` / `x-codegen-annotations` in the YAML.** Constraints that the
   generator cannot honour would be documentation masquerading as enforcement.

> **Corrections on record.** Two recommendations in earlier drafts of this design were wrong and
> were corrected during review. First, replacing the provided generator with
> `openapi-generator-maven-plugin` — rejected for leaving the Quarkus codegen lifecycle. Second,
> `x-codegen-annotations` as the mechanism for field constraints — proven unworkable by the
> experiment above, which was run only because the claim was challenged rather than accepted.
> Both are recorded in ADR-0005 and in `docs/AI_COLLABORATION.md`.

This investigation is itself the substance of the answer to Question 2 (§10).

### Mapping

Hand-written, in a single small mapper. MapStruct was evaluated against the stated criterion — adopt
only if it reduces code substantially — and **rejected on the numbers**: four mappings, ~37 hand-written
lines versus ~30 with MapStruct plus build configuration and an extra extension. Roughly break-even.
The genuine argument for it (`unmappedTargetPolicy = ERROR` catching the dropped-`id` defect at
compile time) is a correctness argument, not a line-count one, and does not meet the criterion.
Recorded in ADR-0006. Lombok is likewise not introduced: the entities are public-field POJOs and
Quarkus + Lombok + annotation processors is friction for no gain across four small classes.

### Error model

`WarehouseDomainException` as the base type, with `WarehouseNotFoundException` → **404**,
`DuplicateBusinessUnitCodeException` → **409**, and capacity / location / replacement violations →
**400**. A single `ExceptionMapper` translates them, emitting the same JSON error shape the existing
`ErrorMapper` already produces so the API stays internally consistent.

**409 and 422 must be added to `warehouse-openapi.yaml`** on `POST /warehouse` only. That is the sole
operation that can collide on business unit code: the replacement path *reuses* the code by design,
so a duplicate there is not an error condition. The replacement path's existing 400 already covers
its own violations. Returning a status the contract does not describe would contradict the claim
that the YAML is authoritative — the contract changes first, then the code follows it, which is the
whole point of working contract-first and is worth stating in the Question 2 answer.

**Adding `quarkus-hibernate-validator` changes the error shape for null bodies.** Quarkus registers
its own `ResteasyReactiveViolationExceptionMapper`, which emits a `ViolationReport` rather than the
`{exceptionType, code, error}` shape used everywhere else in this API. Since §5 claims internal
consistency, a mapper for `ResteasyReactiveViolationException` producing the house shape is part of
this work, not an afterthought.

---

## 6. Store legacy sync

```java
public record StoreChangedEvent(Long id, String name, int quantityProductsInStock,
                                Operation operation) {
  public enum Operation { CREATED, UPDATED }
}
```

The event is **constructed inside the transaction, at the call site**, and observed with
`@Observes(during = TransactionPhase.AFTER_SUCCESS)`, which invokes `LegacyStoreManagerGateway`.

**The event carries a snapshot, not the entity — and this is the reason for choosing an event over
a `runAfterCommit` helper.** The obvious alternative,
`TransactionSynchronizationRegistry.registerInterposedSynchronization` with a lambda closing over
the Panache entity, reads that entity *after* the transaction has ended, when the persistence
context is gone. It happens to work here because `Store` has eagerly loaded public fields, but it
breaks the moment anything is lazy or the entity is mutated after the call site. A snapshot
guarantees that what reaches the legacy system is what was committed. Building it from the
*persisted* `entity` also fixes defect 2.2.1 by construction — the legacy system finally receives a
record with an id.

The event is additionally what makes the post-commit guarantee testable without a real transaction,
by swapping in a recording observer. That is the concrete artefact the Question 3 answer points at.

**`Operation` has exactly two members.** No `DELETE`: the gateway exposes no delete operation, so a
discriminator with nothing behind it would be dead code.

**The observer reconstructs a transient `Store` from the snapshot.** `LegacyStoreManagerGateway`'s
signature is `(Store)` and the gateway stays byte-identical, so the observer builds a detached
`Store` instance from the record's fields and passes that. Noting it here so it is not rediscovered
mid-implementation and mistaken for a reason to abandon the snapshot.

A rollback delivers nothing, which is the entire point of the task.

**Known limitation, stated rather than hidden:** `AFTER_SUCCESS` gives *at-most-once* delivery. If
the legacy write fails after commit, the two systems diverge silently. The production answer is a
transactional outbox with a retrying publisher; deliberately out of scope, recorded in ADR-0004
instead of built.

### PATCH

The PATCH handler takes `com.fasterxml.jackson.databind.node.ObjectNode` rather than `Store`, and
applies fields only when `has(...)` reports them present.

The absent-versus-zero problem is a limitation of the deserialised bean, not of the protocol. On the
raw JSON tree the two are cleanly distinguishable, and this is the only approach that preserves true
PATCH semantics: a client **can** explicitly send `{"quantityProductsInStock": 0}` and mean it,
which every "skip if zero" heuristic silently forbids.

`Store.quantityProductsInStock` **stays a primitive `int`**. Boxing a persisted Panache field to
solve a request-binding problem would leak an API concern into the persistence model and introduce
a null the column does not want.

Missing name on PATCH stays a 422 only when the key is *present* and blank or null; an omitted name
is simply not applied. A null or absent body is a 400.

### Scope decision: delete propagation

`LegacyStoreManagerGateway` exposes only `createStoreOnLegacySystem` and `updateStoreOnLegacySystem`.
It represents a system we do not own, so **it stays byte-identical to the baseline** — inventing a
`deleteStoreOnLegacySystem` would be fabricating an external contract we have no authority over.

The assignment asks that gateway calls happen *after* the database commit, not that the legacy
register be complete. Delete propagation is therefore **out of scope**, and the post-commit
guarantee is applied only to the operations the gateway actually exposes: create, and update
(including PATCH).

This is documented in `QUESTIONS.md`, where written answers are graded — not in the README, and not
as an ADR, because it is a scope decision rather than an architectural one.

Defects 2.2.1, 2.2.2 and 2.2.3 are fixed as part of this work, since correct propagation is
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

**Hand-coded, not contract-first — deliberately, and this is the point.** The bonus is a new API
whose consumers do not exist yet, added under time pressure. §5 established that the warehouse
contract is worth generating because it is the published workflow other systems depend on; applying
the same ceremony to an unproven internal endpoint would be governance without a consumer to govern.
Building one of each, and being able to say why, is a stronger answer to Question 2 than doing both
the same way. Documented in ADR-0008.

**Known limitation:** the count-based rules are check-then-act and therefore racy under concurrent
requests. The unique constraint prevents duplicate triples but not a simultaneous pair of inserts
that each individually satisfy a count. Serialisable isolation or a locking read would close it.
Stated in ADR-0009 rather than papered over.

---

## 8. Testing strategy

TDD on the domain, committed red → green → refactor so the history is itself the evidence.

| Layer | Tooling | Covers |
|---|---|---|
| Domain unit | plain JUnit 5, no Quarkus, `InMemoryWarehouseStore` + `StaticLocationResolver` fakes | every rule in §4, both happy and violation paths |
| REST | `@QuarkusTest` + RestAssured | status codes, error shape, null-body rejection, post-commit sync ordering |
| Integration | `@QuarkusIntegrationTest` (`WarehouseEndpointIT`, uncommented) | packaged application against a real database |

### Making the integration test actually run — verified end to end

Per defect 2.2.11, `maven-failsafe-plugin` must be added to the **default** build section, not left
in the `native` profile. Without it `./mvnw verify` goes green while never executing the IT — a
false pass on success criterion #2, on the one test that proves the archive-and-exclude behaviour.

Adding Failsafe alone is not sufficient, and the reason was found by running it rather than by
reasoning about it.

**Dev Services cannot start a database on the development machine.** Docker Desktop 29.2.0 answers
`/info` with HTTP 400 to the `docker-java` client bundled with Quarkus 3.13.3, so every Testcontainers
strategy fails — `EnvironmentAndSystemPropertyClientProviderStrategy`,
`UnixSocketClientProviderStrategy` and `DockerDesktopClientProviderStrategy` alike — and
`DevServicesDatasourceProcessor` aborts the build. The `docker` CLI works normally; this is a
client/API-version incompatibility, not a broken Docker install. `@QuarkusTest` therefore cannot run
at all in the default configuration, which would make TDD impossible before a line was written.

**Resolution, measured green:** run a real PostgreSQL on port 15432 with the `quarkus_test`
credentials — exactly what the provided `java-assignment/README.md` already instructs — and point
the test profile at it with Dev Services disabled. `./mvnw verify` then reports:

```
Tests run: 2, Failures: 0, Errors: 0   (surefire — ProductEndpointTest, LocationGatewayTest)
Tests run: 2, Failures: 0, Errors: 0   (failsafe — WarehouseEndpointIT)
BUILD SUCCESS
```

The test configuration goes in **`src/test/resources/application.properties`**, which leaves
`src/main/resources/application.properties` byte-identical to the baseline. This was verified: the
file diffs clean against `baseline` and the build is still green.

```properties
# src/test/resources/application.properties  (new file; main config untouched)
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=quarkus_test
quarkus.datasource.password=quarkus_test
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:15432/quarkus_test
quarkus.devservices.enabled=false
```

Two properties of this arrangement are worth noting. The `@QuarkusIntegrationTest` runs the packaged
application under the **prod** profile, and the provided `%prod.quarkus.datasource.jdbc.url` already
points at `localhost:15432` with exactly these credentials — so the IT connects through
**completely unmodified** provided configuration. And the whole class of Testcontainers/Docker-version
flakiness disappears from the build, on every machine.

The database comes from a `docker-compose.yml` for local use and a service container in CI, so one
documented command works in both places. The trade-off — losing Dev Services' zero-configuration
convenience — is recorded in ADR-0010 along with the measurement that forced it.

Three traps in the commented-out block have to be handled when uncommenting it:

- **Shared database state.** `testSimpleCheckingArchivingWarehouses` archives id 1; if it runs
  before `testSimpleListWarehouses`, that test's assertion on `MWH.001` fails. The archiving test
  will therefore create and archive **its own** warehouse rather than mutating seed row 1, so the
  two tests are order-independent. Ordering annotations would mask the coupling rather than remove
  it.
- **Assert on the business unit code, never the location.** The commented block asserts
  `not(containsString("ZWOLLE-001"))`. Locations are shared between warehouses — `AMSTERDAM-001`
  carries both seed `MWH.012` and anything a test creates there — so a location-based absence
  assertion is wrong the moment a second warehouse shares the location. Business unit codes are
  unique among active warehouses, which is exactly what the assertion needs. The location the test
  creates in must also have spare warehouse count and spare capacity per §4.
- **Missing import.** The commented block uses `not(...)`; the file imports only `containsString`.

`LocationGatewayTest` is likewise commented out and is the test for Task 1. It is restored as the
first red test of the TDD sequence.

`@QuarkusTest` classes share one database too: `ProductEndpointTest` already deletes product 1, and
new store and warehouse test classes will run against the same schema. Each new test class creates
the rows it asserts on rather than depending on seed data, except where a test is specifically about
the seeded fixtures.

### Named store-side tests

Three cases from §6 are called out because they are the ones a plausible-but-wrong implementation
passes silently:

- `PATCH {"name":"x"}` against a store with stock 5 leaves stock at **5** (catches the wipe).
- `PATCH {"quantityProductsInStock":0}` sets stock to **0** (catches every "skip if zero"
  heuristic — the case that distinguishes a correct implementation from a merely plausible one).
- A rolled-back transaction produces **zero** gateway invocations; a committed one produces exactly
  one, carrying a non-null id.

One warehouse-side REST test is singled out for the same reason: **`POST /warehouse` with a null
body returns 400**. Fixes 2.2.9 and 2.2.10 pull in opposite directions — removing the redeclared
`@NotNull` (2.2.10) is only safe if the constraint is still inherited from the generated interface
and enforced (2.2.9). This test is the gate that proves one fix did not silently undo the other.

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

**Droppable under time pressure.** Mutation triage is the largest variable-cost item in a brief
scoped at roughly four hours. If time runs short the ordering is: keep the domain tests and the
threshold, drop the triage of low-value survivors, and record the achieved score honestly rather
than tuning the threshold down to meet it. A real 71% with an explanation is worth more than a
manufactured 85%.

---

## 9. Repository, CI and process

### Layout

```
/
├─ README.md                  rewritten as the submission front door
├─ .gitignore                 added in the baseline commit
├─ docker-compose.yml         new — PostgreSQL on 15432 for tests
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

- **build** — a `postgres` service container published on **15432** with the `quarkus_test`
  credentials, then `./mvnw -B verify`. Not Dev Services: §8 records why, and a service container
  behaves identically on every machine.
- **mutation** — `./mvnw -Pmutation …` publishing the PIT HTML report as an artifact. Needs no
  database; the domain tests are plain JUnit.

`mvnw` must be made executable (defect 2.2.6) before CI can work at all.

CI must also **assert that the integration test actually ran**, not merely that the build passed —
defect 2.2.11 is precisely a green build that silently skipped it. Failing the job when
`WarehouseEndpointIT` is absent from the Failsafe reports costs one step and closes the hole
permanently.

**No JaCoCo.** Mutation score strictly dominates line coverage as a quality signal; publishing both
invites a reviewer to anchor on the weaker number. One strong metric, defended.

### Decision records

| ADR | Decision |
|---|---|
| 0001 | Grandfather the seed data; validate on write only |
| 0002 | `{id}` is the numeric database id |
| 0003 | Archive as a soft state change; `WarehouseStore.remove` repurposed as archival |
| 0004 | Post-commit legacy sync via CDI event carrying an immutable snapshot; at-most-once accepted, outbox out of scope |
| 0005 | Contract-first retained on the Quarkus/Apicurio generator; field-level bean validation proven unreachable; validation split between the engine and the use cases |
| 0006 | MapStruct evaluated and rejected on measured line count |
| 0007 | Mutation testing scoped to the domain layer |
| 0008 | Bonus fulfilment API hand-coded rather than generated |
| 0009 | Fulfilment count rules are check-then-act; race documented, not closed |
| 0010 | Dev Services disabled in favour of a real PostgreSQL on 15432, forced by a measured Docker Desktop / `docker-java` incompatibility |

### Evidence of AI direction

`docs/AI_COLLABORATION.md` records the workflow — brainstorm → spec → plan → TDD → mutation triage —
and, candidly, **where the AI was wrong and how it was caught**. Three entries already exist from
the design session:

1. Recommending replacement of the provided OpenAPI generator, corrected to staying inside the
   Quarkus codegen lifecycle.
2. Reporting `io.quarkiverse.mapstruct:quarkus-mapstruct` as non-existent when the Maven Central
   query was simply malformed.
3. Asserting `x-codegen-annotations` as a working mechanism for field constraints without having
   executed it — corrected by being told to test the premise, which disproved it.

Each was caught by a human challenging a claim rather than accepting it, and in the third case the
challenge produced the experiment that became the substance of the Question 2 answer. A record of
the machine being corrected is stronger evidence of competent direction than a clean narrative.

---

## 10. Written deliverables

`QUESTIONS.md` and `CASE_STUDY.md` are answered in place, inside the existing template blocks, in
English.

**Answers argue from reasoning and from this codebase — they make no personal or biographical
claims.** Where experience would normally be cited, the answer instead reasons from the concrete
situation in front of it. This is a deliberate constraint agreed during design.

Scenario 1 of the case study explicitly invites "previous experiences that you have". The answer
must **visibly substitute** for that rather than appear to have skipped the prompt — opening by
reasoning from the failure modes the domain actually exhibits, so the omission reads as a choice
rather than an oversight.

- **Q1 — persistence strategies.** The codebase genuinely mixes three: active-record Panache
  (`Store`), Panache repository (`ProductRepository`), and a hexagonal port (`WarehouseStore`).
  Position: refactor by risk, not for symmetry — demonstrated in the diff. Includes the concrete
  recommendation that `drop-and-create` + `import.sql` must become versioned migrations before
  production.
- **Q2 — contract-first versus code-first.** Answered first-hand from §5 and §7: what the shipped
  generator can and cannot express (measured, with the configuration matrix), the discovery that the
  shipped `@NotNull` was inert, adding 409 to the contract *before* the code returned it, and
  deliberately hand-coding the bonus API to show that the choice is per-consumer rather than
  per-codebase.

  This is also where the delete-propagation scope decision from §6 is documented:

  > The legacy gateway exposes no delete operation, so `DELETE /store/{id}` leaves an orphan record
  > in the legacy register. Closing this would require either a change to the legacy contract
  > (outside my control) or an outbox with periodic reconciliation to flag records without a
  > counterpart. I scoped it out and applied the post-commit guarantee only to the operations the
  > gateway supports, rather than extending provided code to make the problem disappear.
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
| ~~`x-codegen-annotations` not yet exercised~~ | **Resolved by experiment** (§5). The mechanism does not work for field constraints; the design no longer depends on it. |
| Adding `quarkus-hibernate-validator` throws at startup | Caused by the redeclared `@NotNull` (defect 2.2.10). A codebase-wide grep confirms only two sites, both in `WarehouseResourceImpl`. Remove them in the same commit that adds the dependency, and start the app once to confirm. |
| `@QuarkusIntegrationTest` needs a packaged app, a database, **and Failsafe** | **Resolved and measured** (§8): Failsafe in the default build, real PostgreSQL on 15432, Dev Services off. `./mvnw verify` runs all four tests and reports `BUILD SUCCESS`. CI additionally asserts the IT appears in the Failsafe reports, since a silent skip looks identical to a pass. |
| Dev Services unusable locally (Docker Desktop 29.2.0 vs bundled `docker-java`) | Root-caused by running it, not inferred (§8). Removed from the design entirely rather than worked around per-machine. |
| Mutation threshold too aggressive for the time budget | Start at 85%, triage survivors, and record the achieved score honestly rather than lowering the bar to meet it. Triage is explicitly droppable (§8). |
| Generator upgrade appears attractive later | 2.9.0 does not compile in this project without `quarkus-smallrye-openapi`, and still emits no field constraints (§5). Recorded so the option is not retried blind. |
