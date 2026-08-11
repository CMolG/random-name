# FCS Warehouse Fulfilment — code assignment

A Quarkus monolith managing warehouses, stores, products, and which warehouses fulfil which products
for which stores. This repository contains the assignment as provided plus the work done on top of
it.

The tasks are in [`java-assignment/CODE_ASSIGNMENT.md`](java-assignment/CODE_ASSIGNMENT.md); the
domain briefing is in [`case-study/BRIEFING.md`](case-study/BRIEFING.md). Some of the provided code
is based on the [Quarkus quickstarts](https://github.com/quarkusio/quarkus-quickstarts).

## Running it

Java 21 and Docker are required. Quarkus 3.13 does not support JDK 25.

```sh
docker compose up -d                 # PostgreSQL on 15432
cd java-assignment && ./mvnw verify  # 68 tests + 2 integration tests
```

`./mvnw quarkus:dev` then serves the API on `http://localhost:8080`.

**What the database requirement actually is.** Dev Services and Testcontainers are deliberately
off — [ADR-0010](docs/adr/0010-real-postgresql-instead-of-dev-services.md) records the measured
reason — so nothing here starts a container for you. What the tests need is *a PostgreSQL listening
on **15432** with the user, password and database all `quarkus_test`*. `docker-compose.yml` is the
convenient way to get one; a locally installed PostgreSQL on that port works identically. Docker is
the convenience, not the dependency. The port and credentials are not chosen here — they are the
ones the provided `java-assignment/README.md` already documents, which is also why the packaged-app
integration test needs no configuration of its own.

**If you skip `docker compose up -d`**, the build fails with a connection refusal rather than
anything mysterious:

```
Connection to localhost:15432 refused. Check that the hostname and port are correct
and that the postmaster is accepting TCP/IP connections.
```

**What runs with no database at all.** The domain tests and the whole mutation profile need
nothing running — which is why the CI mutation job has no service container:

```sh
cd java-assignment
./mvnw test -Dtest='*UseCaseTest,LocationGatewayTest'   # 30 tests, no database
./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

## What was built

| Assignment task | Where |
|---|---|
| `LocationGateway.resolveByIdentifier` | `location/LocationGateway.java` |
| Legacy store sync only after commit | `stores/StoreChangedEvent.java`, `stores/LegacyStoreSyncObserver.java` |
| Warehouse create, get, list, archive, replace | `warehouses/**` |
| Bonus — fulfilment associations | `fulfilment/**` |
| Written answers | [`java-assignment/QUESTIONS.md`](java-assignment/QUESTIONS.md), [`case-study/CASE_STUDY.md`](case-study/CASE_STUDY.md) |

## Architecture

The warehouse subdomain is ports-and-adapters, as the provided scaffolding intends: use cases hold
the rules and depend only on `WarehouseStore` and `LocationResolver`, with Panache and the REST layer
as adapters on either side. The business rules are therefore testable without a container, which is
what makes mutation testing worthwhile.

`Product` and `Store` are left as plain Panache CRUD **on purpose**. They carry no business rules,
and wrapping ports around them would add roughly fifteen classes of ceremony for no behavioural gain.
Question 1 asks whether the mixed strategies should be unified; leaving them alone lets the answer —
refactor by risk, not for symmetry — be visible in the diff rather than merely asserted.

The warehouse API is generated from `warehouse-openapi.yaml`; the bonus fulfilment API is
hand-written. One of each, deliberately, and Question 2 explains why the choice is per consumer
rather than per codebase.

## Defects found in the provided code

Verified by running them, not assumed:

| # | Defect |
|---|---|
| 1 | `StoreResource.update`/`patch` sent the **request body** to the legacy system rather than the persisted entity, so it received a record with a null id |
| 2 | `PATCH /store/{id}` could never set a stock to zero, and wiped a non-zero stock when the field was omitted — the guard tested the *stored* value of a primitive `int`, where absent and zero are indistinguishable |
| 3 | `PATCH` rejected an absent name with 422, contradicting the semantics of a partial update |
| 4 | `DELETE /store/{id}` never notifies the legacy system — scoped out rather than fixed, and argued in the Question 2 answer |
| 5 | `pom.xml` set `maven.compiler.release=17` alongside `<source>11</source><target>11</target>` |
| 6 | `mvnw` was not executable (mode 644), so CI could not have run at all |
| 7 | The root README linked to `assignment/CODE_ASSIGNMENT.md`, which does not exist |
| 8 | `toWarehouseResponse` never set the response `id`, so the API could not return the identifier its own schema advertises |
| 9 | The generated `@NotNull` was inert — no validation engine was on the runtime classpath, so `required: true` was unenforced |
| 10 | `WarehouseResourceImpl` re-declared `@NotNull` on overriding parameters, which Jakarta Validation forbids and which throws at startup once an engine exists |
| 11 | **`WarehouseEndpointIT` never ran.** Failsafe was declared only in the `native` profile, so `mvn verify` passed while silently skipping the one test that exercises the packaged application |

Defects 9 and 10 have to be fixed together: either alone breaks the build or leaves the body
unvalidated. `postingANullBodyIsRejectedWithFourHundred` is the test that proves they did not cancel
out. Defect 11 is why CI asserts the integration test's report exists rather than trusting a green
build.

The seed data also contradicts the rules — `MWH.001` sits at `ZWOLLE-001` with 2.5× its location's
maximum capacity. That is [grandfathered, not edited away](docs/adr/0001-grandfather-the-seed-data.md).

## Testing

| Layer | Tooling |
|---|---|
| Domain rules | plain JUnit 5, in-memory port fakes, no container |
| REST | `@QuarkusTest` + RestAssured, against PostgreSQL |
| Packaged application | `@QuarkusIntegrationTest` |
| Rule strength | PIT mutation testing, domain-scoped |

Mutation testing covers **44 mutants across the four rule-bearing domain classes** —
`CreateWarehouseUseCase`, `ReplaceWarehouseUseCase`, `ArchiveWarehouseUseCase`, `LocationGateway`.
Adapters, entities and container-driven tests are excluded
[per ADR-0007](docs/adr/0007-mutation-testing-scoped-to-the-domain.md). It scored the green suite at
**84%**, and every one of the 7 survivors was a real gap; after triage, **44 killed**, with line
coverage unchanged at 99% throughout.
[What it caught](docs/AI_COLLABORATION.md#what-mutation-testing-caught-that-a-green-build-did-not).

That score describes the rule layer, not the application. The fulfilment limits are outside it —
`FulfilmentService` needs a container to test — and were verified instead by a one-off manual
mutation: flipping each `>=` to `>` and confirming exactly the three boundary tests failed.

## Decisions

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-grandfather-the-seed-data.md) | Grandfather the seed data; validate on write only |
| [0002](docs/adr/0002-id-is-the-numeric-database-id.md) | `{id}` is the numeric database id |
| [0003](docs/adr/0003-archive-rather-than-delete.md) | Archive as a soft state change; `remove` repurposed as archival |
| [0004](docs/adr/0004-post-commit-legacy-sync.md) | Post-commit legacy sync via a CDI event carrying an immutable snapshot |
| [0005](docs/adr/0005-contract-first-with-the-provided-generator.md) | Keep the provided generator; validation split between the engine and the use cases |
| [0006](docs/adr/0006-mapstruct-rejected.md) | MapStruct evaluated and rejected on measured line count |
| [0007](docs/adr/0007-mutation-testing-scoped-to-the-domain.md) | Mutation testing scoped to the domain |
| [0008](docs/adr/0008-bonus-api-hand-coded.md) | Bonus fulfilment API hand-coded rather than generated |
| [0009](docs/adr/0009-fulfilment-count-rules-are-check-then-act.md) | Fulfilment count rules are check-then-act; the race is documented |
| [0010](docs/adr/0010-real-postgresql-instead-of-dev-services.md) | Real PostgreSQL instead of Dev Services |

## Reviewing this

The first commit is the assignment exactly as provided, tagged `baseline`:

```sh
git diff baseline..HEAD           # everything written on top of the provided code
git log --oneline baseline..HEAD  # the order it was written in
```

Work proceeded one GitHub issue per task, following the repository's own `CONTRIBUTING.md`
convention: a branch per issue, Conventional Commits referencing it, and a pull request per branch.

[`docs/AI_COLLABORATION.md`](docs/AI_COLLABORATION.md) records how the work was directed and,
specifically, three occasions where the assistant was wrong and how it was caught. The design spec
and implementation plan are in [`docs/superpowers/`](docs/superpowers/).
