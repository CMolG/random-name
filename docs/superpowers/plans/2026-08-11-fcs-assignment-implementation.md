# FCS Warehouse Fulfilment Assignment — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete every stubbed piece of the FCS warehouse assignment — location resolution, post-commit legacy store sync, the full warehouse lifecycle, and the fulfilment bonus — with tests that demonstrably assert something, and answer both written deliverables.

**Architecture:** Ports-and-adapters for the warehouse subdomain (already scaffolded in the baseline), plain Panache CRUD left deliberately in place for `Product` and `Store`. Contract-first: the OpenAPI YAML is authoritative and changes before the code that satisfies it. Business rules live in use cases behind ports, so they are testable without a container.

**Tech Stack:** Java 21 (compiled `release 17`), Quarkus 3.13.3, Hibernate ORM with Panache, PostgreSQL, JUnit 5, RestAssured, PIT 1.19.1 mutation testing, GitHub Actions.

**Spec:** [`docs/superpowers/specs/2026-08-11-fcs-assignment-design.md`](../specs/2026-08-11-fcs-assignment-design.md) — section references below (§N) point there.

---

## Before you start

**Prerequisites**

```bash
export JAVA_HOME=/Users/carlos/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home
cd "/Volumes/Samsung NVME/IdeaProjects/random-name/java-assignment"
```

JDK 21 is required. The system default on this machine is JDK 25, which Quarkus 3.13 does not support. **Do not** rely on Dev Services — it cannot start on this machine (§8); Task 0 sets up a real database instead.

**Branch and commit conventions** — `CONTRIBUTING.md` asks for Conventional Commits referencing the issue. One branch per issue, one PR per branch:

```bash
git switch -c feat/1-location-gateway main   # branch name carries the issue number
```

Every commit message ends with the issue reference and the co-author trailers:

```
feat: resolve locations by identifier. refs #1

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01FwAwzVtfbZw9qrdvydeLdE
```

Shown once here; assume it on every commit below rather than repeating it.

**Ground rules**

1. These files stay **byte-identical** to `baseline`: `CODE_ASSIGNMENT.md`, `BRIEFING.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`, `LegacyStoreManagerGateway.java`, `src/main/resources/application.properties`, `import.sql`. Verify at the end with `git diff baseline..HEAD --stat`.
2. Never edit `import.sql` to make a test pass. The seed data violates the rules on purpose (§2.3); that is a finding, not a bug to hide.
3. Red before green. If a test passes the first time you run it, it is testing nothing — fix the test before writing the code.

---

## File structure

**Created**

| Path | Responsibility |
|---|---|
| `docker-compose.yml` (repo root) | PostgreSQL on 15432 for tests |
| `src/test/resources/application.properties` | Test datasource; Dev Services off |
| `…/warehouses/domain/exceptions/WarehouseDomainException.java` | Base type for domain failures |
| `…/warehouses/domain/exceptions/WarehouseNotFoundException.java` | → 404 |
| `…/warehouses/domain/exceptions/DuplicateBusinessUnitCodeException.java` | → 409 |
| `…/warehouses/domain/exceptions/InvalidWarehouseDataException.java` | → 400 (field + business rules) |
| `…/warehouses/domain/exceptions/ClientSuppliedIdException.java` | → 422 |
| `…/warehouses/adapters/restapi/WarehouseExceptionMapper.java` | Domain exceptions → HTTP, house JSON shape |
| `…/warehouses/adapters/restapi/ValidationExceptionMapper.java` | Bean-validation failures → same JSON shape |
| `…/stores/StoreChangedEvent.java` | Immutable post-commit snapshot |
| `…/stores/LegacyStoreSyncObserver.java` | `AFTER_SUCCESS` observer → gateway |
| `…/fulfilment/*` | Bonus vertical slice |
| `src/test/java/…/usecases/InMemoryWarehouseStore.java` | Test double for the `WarehouseStore` port |
| `src/test/java/…/usecases/StaticLocationResolver.java` | Test double for `LocationResolver` |
| `src/test/java/…/stores/RecordingLegacyStoreManagerGateway.java` | CDI alternative recording gateway calls |

**Modified**

| Path | Change |
|---|---|
| `pom.xml` | Failsafe into default build, drop stale `source`/`target`, add `quarkus-hibernate-validator`, PIT in a `mutation` profile |
| `mvnw` | mode 644 → 755 |
| `…/location/LocationGateway.java` | `@ApplicationScoped` + implement `resolveByIdentifier` |
| `…/warehouses/domain/models/Warehouse.java` | add `id` |
| `…/warehouses/domain/ports/WarehouseStore.java` | add `findActiveById(Long)` — see Task 2a Step 2 for why not `findById` |
| `…/warehouses/adapters/database/WarehouseRepository.java` | implement 4 stubs, fix `getAll` to exclude archived |
| `…/warehouses/domain/usecases/*.java` | implement all three |
| `…/warehouses/adapters/restapi/WarehouseResourceImpl.java` | implement 4 handlers, inject the **port**, drop redeclared `@NotNull` |
| `…/stores/StoreResource.java` | fire events; `ObjectNode` PATCH |
| `src/main/resources/openapi/warehouse-openapi.yaml` | add 409 + 422 to `POST /warehouse` |
| `README.md` (root) | rewrite as submission front door; fix broken link |
| `java-assignment/QUESTIONS.md`, `case-study/CASE_STUDY.md` | answers |

---

## Task 0: Make the build able to run tests at all — refs #5

Nothing below can be verified until this works. Do it first.

**Files:**
- Modify: `java-assignment/pom.xml`
- Modify: `java-assignment/mvnw` (permissions)
- Create: `java-assignment/src/test/resources/application.properties`
- Create: `docker-compose.yml` (repo root)

- [ ] **Step 1: Branch**

```bash
git switch -c chore/5-build-foundation main
```

- [ ] **Step 2: Make the wrapper executable**

```bash
git update-index --chmod=+x java-assignment/mvnw
chmod +x java-assignment/mvnw
git ls-files -s java-assignment/mvnw   # expect mode 100755
```

- [ ] **Step 3: Create `docker-compose.yml` at the repo root**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: fcs-postgres
    environment:
      POSTGRES_USER: quarkus_test
      POSTGRES_PASSWORD: quarkus_test
      POSTGRES_DB: quarkus_test
    ports:
      - "15432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quarkus_test"]
      interval: 2s
      timeout: 3s
      retries: 15
```

Port 15432 and these credentials are not arbitrary — they are what the provided `java-assignment/README.md` already documents and what `%prod.quarkus.datasource.jdbc.url` already points at, which is why the packaged-app integration test needs no config change.

- [ ] **Step 4: Create `java-assignment/src/test/resources/application.properties`**

```properties
# Test datasource. Kept out of src/main/resources so the provided
# application.properties stays byte-identical to the baseline.
#
# Dev Services is disabled deliberately: Docker Desktop 29.2.0 answers /info
# with HTTP 400 to the docker-java client bundled with Quarkus 3.13.3, so every
# Testcontainers strategy fails and the build aborts. See ADR-0010.
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=quarkus_test
quarkus.datasource.password=quarkus_test
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:15432/quarkus_test
quarkus.devservices.enabled=false
```

- [ ] **Step 5: Add Failsafe to the default build in `pom.xml`**

Insert immediately **before** the `quarkus-maven-plugin` entry inside `<build><plugins>`:

```xml
<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${surefire-plugin.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
            <configuration>
                <systemPropertyVariables>
                    <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                    <maven.home>${maven.home}</maven.home>
                </systemPropertyVariables>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 6: Remove the stale compiler levels**

In the `maven-compiler-plugin` configuration, delete these two lines, leaving `maven.compiler.release=17` authoritative:

```xml
<source>11</source>
<target>11</target>
```

- [ ] **Step 7: Add hibernate-validator**

After the `quarkus-jdbc-postgresql` dependency:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
```

- [ ] **Step 8: Remove the redeclared `@NotNull`**

`WarehouseResourceImpl.java` lines 22 and 41 carry `@NotNull` on overriding parameters. Jakarta Validation forbids this and it throws `ConstraintDeclarationException` at startup now that an engine exists. Delete both annotations and the now-unused `import jakarta.validation.constraints.NotNull;`. The constraint is still inherited from the generated interface.

- [ ] **Step 9: Start the database and verify the whole thing**

```bash
docker compose up -d
cd java-assignment && ./mvnw -B verify
```

Expected: `BUILD SUCCESS`, with **four** tests across two plugins:

```
Tests run: 2 ... surefire   (ProductEndpointTest, LocationGatewayTest)
Tests run: 2 ... failsafe   (WarehouseEndpointIT)
```

If Failsafe reports `Tests run: 0`, the plugin is misplaced — the build passing is not sufficient evidence (that was defect 2.2.11).

- [ ] **Step 10: Confirm the provided config is untouched**

```bash
git diff baseline..HEAD -- java-assignment/src/main/resources/application.properties
```

Expected: **empty output**.

- [ ] **Step 11: Commit and open the PR**

```bash
git add -A
git commit -m "chore: make the build able to run integration tests. refs #5"
git push -u origin chore/5-build-foundation
gh pr create --fill
```

---

## Task 1: LocationGateway — refs #1

**Files:**
- Modify: `src/main/java/…/location/LocationGateway.java`
- Test: `src/test/java/…/location/LocationGatewayTest.java`

- [ ] **Step 1: Branch**

```bash
git switch -c feat/1-location-gateway main
```

- [ ] **Step 2: Write the failing tests** — replace the whole body of `LocationGatewayTest.java`

```java
package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  private final LocationGateway locationGateway = new LocationGateway();

  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertEquals("ZWOLLE-001", location.identification);
    assertEquals(1, location.maxNumberOfWarehouses);
    assertEquals(40, location.maxCapacity);
  }

  @Test
  public void testWhenResolveUnknownLocationShouldReturnNull() {
    assertNull(locationGateway.resolveByIdentifier("ATLANTIS-001"));
  }

  @Test
  public void testWhenResolveNullOrBlankShouldReturnNull() {
    assertNull(locationGateway.resolveByIdentifier(null));
    assertNull(locationGateway.resolveByIdentifier("   "));
  }

  @Test
  public void testResolveIsCaseSensitiveAndTrimmed() {
    assertEquals("ZWOLLE-001", locationGateway.resolveByIdentifier("  ZWOLLE-001  ").identification);
    assertNull(locationGateway.resolveByIdentifier("zwolle-001"));
  }
}
```

The unknown-location case returning `null` rather than throwing is what lets `CreateWarehouseUseCase` turn it into a 400 with a useful message.

- [ ] **Step 3: Run and confirm they fail**

```bash
./mvnw -B test -Dtest=LocationGatewayTest
```

Expected: FAIL — `UnsupportedOperationException: Unimplemented method 'resolveByIdentifier'`.

- [ ] **Step 4: Implement**

```java
package com.fulfilment.application.monolith.location;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LocationGateway implements LocationResolver {

  private static final List<Location> locations = new ArrayList<>();

  static {
    locations.add(new Location("ZWOLLE-001", 1, 40));
    locations.add(new Location("ZWOLLE-002", 2, 50));
    locations.add(new Location("AMSTERDAM-001", 5, 100));
    locations.add(new Location("AMSTERDAM-002", 3, 75));
    locations.add(new Location("TILBURG-001", 1, 40));
    locations.add(new Location("HELMOND-001", 1, 45));
    locations.add(new Location("EINDHOVEN-001", 2, 70));
    locations.add(new Location("VETSBY-001", 1, 90));
  }

  /**
   * Returns the location with the given identifier, or {@code null} when it does not exist.
   *
   * <p>Null is deliberate rather than an exception: an unknown location is an ordinary client
   * mistake that the calling use case reports as a validation failure, not an exceptional condition.
   */
  @Override
  public Location resolveByIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      return null;
    }
    String normalised = identifier.trim();
    return locations.stream()
        .filter(location -> location.identification.equals(normalised))
        .findFirst()
        .orElse(null);
  }
}
```

`@ApplicationScoped` is required — without a bean-defining annotation, injecting `LocationResolver` fails at build time (§2.1).

- [ ] **Step 5: Run and confirm they pass**

```bash
./mvnw -B test -Dtest=LocationGatewayTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit and PR**

```bash
git add -A && git commit -m "feat: resolve locations by identifier. refs #1"
git push -u origin feat/1-location-gateway && gh pr create --fill
```

---

## Task 2: Warehouse domain — refs #3

The largest task. Sub-tasks 2a–2f each end green and committable.

```bash
git switch -c feat/3-warehouse-operations main
```

### Task 2a: Domain model, port and test doubles

**Files:**
- Modify: `…/warehouses/domain/models/Warehouse.java`, `…/domain/ports/WarehouseStore.java`
- Create: `src/test/java/…/usecases/InMemoryWarehouseStore.java`, `…/usecases/StaticLocationResolver.java`

- [ ] **Step 1: Add `id` to the domain model**

Add to `Warehouse.java`, above `businessUnitCode`:

```java
  // database identity; null until persisted
  public Long id;
```

- [ ] **Step 2: Add `findActiveById` to the port**

Add to `WarehouseStore.java`:

```java
  /**
   * The active warehouse with this database id, or null.
   *
   * <p>Deliberately NOT named findById: WarehouseRepository also implements
   * PanacheRepository<DbWarehouse>, which declares findById(Long) returning DbWarehouse. Two methods
   * with the same erasure and incompatible return types is a hard compile error —
   * "findById(Long) in WarehouseRepository cannot implement findById(Id) in PanacheRepositoryBase".
   * The name also says the thing that matters: archived rows are never returned.
   */
  Warehouse findActiveById(Long id);
```

This naming is not cosmetic. `findById` was verified to fail compilation outright, and no method body
fixes it — the clash is in the signature.

- [ ] **Step 3: Create `InMemoryWarehouseStore`** (test sources)

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory {@link WarehouseStore} so use cases can be tested without a database. */
public class InMemoryWarehouseStore implements WarehouseStore {

  private final List<Warehouse> warehouses = new ArrayList<>();
  private final AtomicLong sequence = new AtomicLong(0);

  @Override
  public List<Warehouse> getAll() {
    return warehouses.stream().filter(w -> w.archivedAt == null).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    warehouse.id = sequence.incrementAndGet();
    warehouses.add(warehouse);
  }

  @Override
  public void update(Warehouse warehouse) {
    // stored by reference; mutations are already visible
  }

  @Override
  public void remove(Warehouse warehouse) {
    warehouse.archivedAt = java.time.LocalDateTime.now();
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    return warehouses.stream()
        .filter(w -> w.archivedAt == null && w.businessUnitCode.equals(buCode))
        .findFirst()
        .orElse(null);
  }

  @Override
  public Warehouse findActiveById(Long id) {
    return warehouses.stream()
        .filter(w -> w.archivedAt == null && id.equals(w.id))
        .findFirst()
        .orElse(null);
  }

  /** Seeds a pre-existing warehouse, bypassing validation — for arranging test fixtures. */
  public Warehouse given(String buCode, String location, int capacity, int stock) {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = buCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    warehouse.createdAt = java.time.LocalDateTime.now();
    create(warehouse);
    return warehouse;
  }
}
```

- [ ] **Step 4: Create `StaticLocationResolver`** (test sources)

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;

/** Delegates to the real reference data — the location table is fixed, so faking it adds nothing. */
public class StaticLocationResolver implements LocationResolver {

  private final LocationGateway delegate = new LocationGateway();

  @Override
  public Location resolveByIdentifier(String identifier) {
    return delegate.resolveByIdentifier(identifier);
  }
}
```

- [ ] **Step 5: Keep the tree compiling**

Adding a method to the port breaks `WarehouseRepository`, which must implement it. Add the stub now,
matching the style of its four existing siblings, so every later TDD cycle has a compiling tree to
run against. Task 2e fills it in.

```java
  @Override
  public Warehouse findActiveById(Long id) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'findActiveById'");
  }
```

Then:

```bash
./mvnw -B test-compile
```

Expected: `BUILD SUCCESS`. If it fails with a `findById` clash you have used the wrong method name —
see Step 2.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add warehouse id to the domain model and a findActiveById port. refs #3"
```

### Task 2b: Domain exceptions

**Files:** create five classes under `…/warehouses/domain/exceptions/`.

- [ ] **Step 1: Write them**

```java
// WarehouseDomainException.java
package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Base type for warehouse rule violations, so the REST adapter maps one hierarchy. */
public abstract class WarehouseDomainException extends RuntimeException {
  protected WarehouseDomainException(String message) {
    super(message);
  }
}
```

```java
// WarehouseNotFoundException.java  → 404
public class WarehouseNotFoundException extends WarehouseDomainException {
  public WarehouseNotFoundException(String message) {
    super(message);
  }
}
```

```java
// DuplicateBusinessUnitCodeException.java  → 409
public class DuplicateBusinessUnitCodeException extends WarehouseDomainException {
  public DuplicateBusinessUnitCodeException(String businessUnitCode) {
    super("An active warehouse with business unit code " + businessUnitCode + " already exists.");
  }
}
```

```java
// InvalidWarehouseDataException.java  → 400
public class InvalidWarehouseDataException extends WarehouseDomainException {
  public InvalidWarehouseDataException(String message) {
    super(message);
  }
}
```

```java
// ClientSuppliedIdException.java  → 422
public class ClientSuppliedIdException extends WarehouseDomainException {
  public ClientSuppliedIdException() {
    super("Id was invalidly set on request.");
  }
}
```

(Each needs its own `package` and, for the subclasses, no import — they share the package.)

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add warehouse domain exception hierarchy. refs #3"
```

### Task 2c: CreateWarehouseUseCase — TDD

**Files:**
- Modify: `…/domain/usecases/CreateWarehouseUseCase.java`
- Test: `src/test/java/…/usecases/CreateWarehouseUseCaseTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.*;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new CreateWarehouseUseCase(store, new StaticLocationResolver());
  }

  private static Warehouse warehouse(String buCode, String location, Integer capacity, Integer stock) {
    var w = new Warehouse();
    w.businessUnitCode = buCode;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }

  @Test
  void createsAValidWarehouseAndStampsCreatedAt() {
    var warehouse = warehouse("MWH.100", "AMSTERDAM-001", 50, 10);

    useCase.create(warehouse);

    assertEquals(1, store.getAll().size());
    assertNotNull(warehouse.createdAt, "createdAt must be stamped by the use case");
    assertEquals(null, warehouse.archivedAt);
  }

  @Test
  void rejectsADuplicateActiveBusinessUnitCode() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 10);

    assertThrows(
        DuplicateBusinessUnitCodeException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-002", 10, 1)));
  }

  @Test
  void allowsReusingTheCodeOfAnArchivedWarehouse() {
    var archived = store.given("MWH.100", "AMSTERDAM-001", 50, 10);
    store.remove(archived);

    useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 20, 5));

    assertEquals(1, store.getAll().size());
  }

  @Test
  void rejectsAnUnknownLocation() {
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "ATLANTIS-001", 10, 1)));
  }

  @Test
  void rejectsWhenTheLocationIsAtItsWarehouseLimit() {
    // TILBURG-001 allows exactly 1 warehouse
    store.given("MWH.200", "TILBURG-001", 30, 5);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.201", "TILBURG-001", 5, 1)));
  }

  @Test
  void rejectsWhenSummedCapacityWouldExceedTheLocationMaximum() {
    // AMSTERDAM-001: max 5 warehouses, max capacity 100
    store.given("MWH.300", "AMSTERDAM-001", 90, 10);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.301", "AMSTERDAM-001", 11, 1)));
  }

  @Test
  void acceptsCapacityThatExactlyFillsTheLocation() {
    store.given("MWH.300", "AMSTERDAM-001", 90, 10);

    useCase.create(warehouse("MWH.301", "AMSTERDAM-001", 10, 1));

    assertEquals(2, store.getAll().size());
  }

  @Test
  void rejectsStockGreaterThanCapacity() {
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, 11)));
  }

  @Test
  void rejectsMissingOrBlankFields() {
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse(null, "AMSTERDAM-001", 10, 1)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("  ", "AMSTERDAM-001", 10, 1)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", null, 10, 1)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", null, 1)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 0, 0)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, null)));
    assertThrows(InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, -1)));
  }

  @Test
  void rejectsAClientSuppliedId() {
    var w = warehouse("MWH.100", "AMSTERDAM-001", 10, 1);
    w.id = 99L;

    assertThrows(ClientSuppliedIdException.class, () -> useCase.create(w));
  }

  @Test
  void ignoresArchivedWarehousesWhenCountingLocationUsage() {
    var archived = store.given("MWH.200", "TILBURG-001", 30, 5);
    store.remove(archived);

    useCase.create(warehouse("MWH.201", "TILBURG-001", 40, 5));

    assertEquals(1, store.getAll().size());
  }
}
```

`acceptsCapacityThatExactlyFillsTheLocation` and `allowsReusingTheCodeOfAnArchivedWarehouse` are boundary cases that a `>` / `>=` slip or an "archived counts too" slip would break. They also kill mutants that the inequality-only tests leave alive.

- [ ] **Step 2: Run and confirm failure**

```bash
./mvnw -B test -Dtest=CreateWarehouseUseCaseTest
```

Expected: compilation failure (the constructor takes one argument today), then failures.

- [ ] **Step 3: Implement**

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.*;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public CreateWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  @Override
  public void create(Warehouse warehouse) {
    validate(warehouse);
    warehouse.createdAt = LocalDateTime.now();
    warehouse.archivedAt = null;
    warehouseStore.create(warehouse);
  }

  /**
   * Applies every creation rule. Also used by replacement, which evaluates these against the state
   * left behind once the predecessor has been archived.
   */
  void validate(Warehouse warehouse) {
    if (warehouse.id != null) {
      throw new ClientSuppliedIdException();
    }
    requireText(warehouse.businessUnitCode, "businessUnitCode");
    requireText(warehouse.location, "location");
    if (warehouse.capacity == null || warehouse.capacity <= 0) {
      throw new InvalidWarehouseDataException("Warehouse capacity must be greater than zero.");
    }
    if (warehouse.stock == null || warehouse.stock < 0) {
      throw new InvalidWarehouseDataException("Warehouse stock must not be negative.");
    }
    if (warehouse.stock > warehouse.capacity) {
      throw new InvalidWarehouseDataException(
          "Warehouse stock " + warehouse.stock + " exceeds its capacity " + warehouse.capacity + ".");
    }
    if (warehouseStore.findByBusinessUnitCode(warehouse.businessUnitCode) != null) {
      throw new DuplicateBusinessUnitCodeException(warehouse.businessUnitCode);
    }

    Location location = locationResolver.resolveByIdentifier(warehouse.location);
    if (location == null) {
      throw new InvalidWarehouseDataException("Location " + warehouse.location + " does not exist.");
    }

    var atLocation = warehouseStore.getAll().stream()
        .filter(w -> location.identification.equals(w.location))
        .toList();

    if (atLocation.size() >= location.maxNumberOfWarehouses) {
      throw new InvalidWarehouseDataException(
          "Location " + location.identification + " already holds its maximum of "
              + location.maxNumberOfWarehouses + " warehouses.");
    }

    int used = atLocation.stream().mapToInt(w -> w.capacity).sum();
    if (used + warehouse.capacity > location.maxCapacity) {
      throw new InvalidWarehouseDataException(
          "Capacity " + warehouse.capacity + " exceeds the remaining capacity of "
              + (location.maxCapacity - used) + " at " + location.identification + ".");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidWarehouseDataException("Warehouse " + field + " must be provided.");
    }
  }
}
```

- [ ] **Step 4: Run and confirm green**

```bash
./mvnw -B test -Dtest=CreateWarehouseUseCaseTest
```

Expected: `Tests run: 11, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: validate and create warehouses. refs #3"
```

### Task 2d: Archive and Replace use cases — TDD

**Files:**
- Modify: `…/usecases/ArchiveWarehouseUseCase.java`, `…/usecases/ReplaceWarehouseUseCase.java`
- Test: the two matching test classes

- [ ] **Step 1: Write `ArchiveWarehouseUseCaseTest`**

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArchiveWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private ArchiveWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new ArchiveWarehouseUseCase(store);
  }

  @Test
  void archivingStampsArchivedAtAndHidesItFromReads() {
    Warehouse warehouse = store.given("MWH.100", "AMSTERDAM-001", 50, 10);

    useCase.archive(warehouse);

    assertNotNull(warehouse.archivedAt);
    assertTrue(store.getAll().isEmpty());
    assertNull(store.findByBusinessUnitCode("MWH.100"));
  }

  @Test
  void archivingAnAlreadyArchivedWarehouseIsNotFound() {
    Warehouse warehouse = store.given("MWH.100", "AMSTERDAM-001", 50, 10);
    useCase.archive(warehouse);

    assertThrows(WarehouseNotFoundException.class, () -> useCase.archive(warehouse));
  }

  @Test
  void archivingNullIsNotFound() {
    assertThrows(WarehouseNotFoundException.class, () -> useCase.archive(null));
  }
}
```

- [ ] **Step 2: Run, confirm failure, then implement**

```java
@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  @Override
  public void archive(Warehouse warehouse) {
    if (warehouse == null || warehouse.archivedAt != null) {
      throw new WarehouseNotFoundException("No active warehouse to archive.");
    }
    warehouse.archivedAt = LocalDateTime.now();
    warehouseStore.update(warehouse);
  }
}
```

`update`, not `remove` — decision 6. Archival is a field mutation on an existing row.

- [ ] **Step 3: Write `ReplaceWarehouseUseCaseTest`**

```java
package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.*;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private ReplaceWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    var create = new CreateWarehouseUseCase(store, new StaticLocationResolver());
    var archive = new ArchiveWarehouseUseCase(store);
    useCase = new ReplaceWarehouseUseCase(store, create, archive);
  }

  private static Warehouse successor(String buCode, String location, int capacity, int stock) {
    var w = new Warehouse();
    w.businessUnitCode = buCode;
    w.location = location;
    w.capacity = capacity;
    w.stock = stock;
    return w;
  }

  @Test
  void replacementArchivesThePredecessorAndCreatesTheSuccessorWithTheSameCode() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 10);

    useCase.replace(successor("MWH.100", "AMSTERDAM-001", 60, 10));

    var active = store.getAll();
    assertEquals(1, active.size());
    assertEquals("MWH.100", active.get(0).businessUnitCode);
    assertEquals(60, active.get(0).capacity);
  }

  @Test
  void replacementFailsWhenNoActiveWarehouseHasThatCode() {
    assertThrows(
        WarehouseNotFoundException.class,
        () -> useCase.replace(successor("MWH.404", "AMSTERDAM-001", 10, 0)));
  }

  @Test
  void successorCapacityMustAccommodateThePredecessorStock() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 30);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.replace(successor("MWH.100", "AMSTERDAM-001", 20, 30)));
  }

  @Test
  void successorStockMustMatchThePredecessorStock() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 30);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.replace(successor("MWH.100", "AMSTERDAM-001", 50, 29)));
  }

  @Test
  void theSeededOverCapacityWarehouseCannotBeReplacedAtItsOwnCapacity() {
    // Mirrors import.sql: MWH.001 sits at ZWOLLE-001 (max capacity 40) with capacity 100.
    store.given("MWH.001", "ZWOLLE-001", 100, 10);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.replace(successor("MWH.001", "ZWOLLE-001", 100, 10)));
  }

  @Test
  void theSeededOverCapacityWarehouseCanBeReplacedWithinItsLocationLimit() {
    store.given("MWH.001", "ZWOLLE-001", 100, 10);

    useCase.replace(successor("MWH.001", "ZWOLLE-001", 40, 10));

    assertEquals(40, store.findByBusinessUnitCode("MWH.001").capacity);
  }

  @Test
  void aFailedReplacementLeavesThePredecessorActive() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 30);

    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.replace(successor("MWH.100", "AMSTERDAM-001", 50, 29)));

    var survivor = store.findByBusinessUnitCode("MWH.100");
    assertNotNull(survivor, "predecessor must remain active when replacement is rejected");
    assertNull(survivor.archivedAt);
  }
}
```

The two `theSeededOverCapacityWarehouse…` tests encode the grandfathering decision (§2.3) as
executable assertions. **They must be two tests with fresh fixtures, not one.** A single test that
asserts the rejection and then performs a successful replacement fails with
`WarehouseNotFoundException`: the creation rules run *after* the archival by design (§4), and
`InMemoryWarehouseStore` has no rollback, so the rejected attempt leaves `MWH.001` already archived.
Against PostgreSQL `@Transactional` rolls that archival back — but the unit tests do not exercise a
transaction, and pretending otherwise would make them lie.

`aFailedReplacementLeavesThePredecessorActive` guards the *replacement-specific* rules — capacity
accommodation and stock matching — which run **before** the archival precisely so a bad request
cannot strand a business unit code. It does not test the creation rules, which are ordered after the
archival and rely on the transaction. The REST test in Task 2f covers that path against a real
database.

- [ ] **Step 4: Implement**

```java
@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final CreateWarehouseUseCase createWarehouse;
  private final ArchiveWarehouseUseCase archiveWarehouse;

  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore,
      CreateWarehouseUseCase createWarehouse,
      ArchiveWarehouseUseCase archiveWarehouse) {
    this.warehouseStore = warehouseStore;
    this.createWarehouse = createWarehouse;
    this.archiveWarehouse = archiveWarehouse;
  }

  /**
   * Archives the active warehouse holding {@code businessUnitCode} and creates its successor under
   * the same code. Replacement-specific rules are checked first, while the predecessor is still
   * active; the creation rules are then evaluated against the state left once it is archived —
   * otherwise the successor would always collide with its own predecessor on code uniqueness and on
   * the location warehouse count.
   */
  @Override
  @Transactional
  public void replace(Warehouse newWarehouse) {
    if (newWarehouse == null || newWarehouse.businessUnitCode == null) {
      throw new InvalidWarehouseDataException("Warehouse businessUnitCode must be provided.");
    }

    Warehouse previous = warehouseStore.findByBusinessUnitCode(newWarehouse.businessUnitCode);
    if (previous == null) {
      throw new WarehouseNotFoundException(
          "No active warehouse with business unit code " + newWarehouse.businessUnitCode + ".");
    }

    if (newWarehouse.capacity == null || newWarehouse.capacity < previous.stock) {
      throw new InvalidWarehouseDataException(
          "New capacity must accommodate the previous stock of " + previous.stock + ".");
    }
    if (newWarehouse.stock == null || !newWarehouse.stock.equals(previous.stock)) {
      throw new InvalidWarehouseDataException(
          "New stock must match the previous stock of " + previous.stock + ".");
    }

    archiveWarehouse.archive(previous);
    createWarehouse.create(newWarehouse);
  }
}
```

`@Transactional` makes archive-plus-create atomic (§4). In the unit tests it is inert — the in-memory store has no transaction — which is exactly why `aFailedReplacementLeavesThePredecessorActive` orders validation *before* the archival rather than relying on rollback.

- [ ] **Step 5: Run both, confirm green, commit**

```bash
./mvnw -B test -Dtest='ArchiveWarehouseUseCaseTest,ReplaceWarehouseUseCaseTest'
git add -A && git commit -m "feat: archive and replace warehouses. refs #3"
```

### Task 2e: Repository adapter

**Files:** modify `…/adapters/database/WarehouseRepository.java`, `…/adapters/database/DbWarehouse.java`

- [ ] **Step 1: Add the reverse mapping to `DbWarehouse`**

```java
  public static DbWarehouse from(Warehouse warehouse) {
    var db = new DbWarehouse();
    db.id = warehouse.id;
    db.businessUnitCode = warehouse.businessUnitCode;
    db.location = warehouse.location;
    db.capacity = warehouse.capacity;
    db.stock = warehouse.stock;
    db.createdAt = warehouse.createdAt;
    db.archivedAt = warehouse.archivedAt;
    return db;
  }
```

And add `warehouse.id = this.id;` as the first line of the existing `toWarehouse()` — without it the API can never return an id (defect 2.2.8).

- [ ] **Step 2: Implement the repository**

```java
@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  /** Active warehouses only — archived rows are history, not inventory. */
  @Override
  public List<Warehouse> getAll() {
    return find("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    DbWarehouse db = DbWarehouse.from(warehouse);
    persist(db);
    warehouse.id = db.id;
  }

  @Override
  @Transactional
  public void update(Warehouse warehouse) {
    DbWarehouse db = findById(warehouse.id);
    if (db == null) {
      throw new WarehouseNotFoundException("Warehouse " + warehouse.id + " does not exist.");
    }
    db.businessUnitCode = warehouse.businessUnitCode;
    db.location = warehouse.location;
    db.capacity = warehouse.capacity;
    db.stock = warehouse.stock;
    db.createdAt = warehouse.createdAt;
    db.archivedAt = warehouse.archivedAt;
  }

  /** Archival, not deletion — see ADR-0003. Kept as an alias so there is one archival path. */
  @Override
  @Transactional
  public void remove(Warehouse warehouse) {
    warehouse.archivedAt = warehouse.archivedAt == null ? LocalDateTime.now() : warehouse.archivedAt;
    update(warehouse);
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse db = find("businessUnitCode = ?1 and archivedAt is null", buCode).firstResult();
    return db == null ? null : db.toWarehouse();
  }

  @Override
  public Warehouse findActiveById(Long id) {
    DbWarehouse db = find("id = ?1 and archivedAt is null", id).firstResult();
    return db == null ? null : db.toWarehouse();
  }
}
```

Note that `update` calls Panache's inherited `findById(warehouse.id)` returning `DbWarehouse`, while
the port method is `findActiveById` returning the domain type. Both coexist only because the names
differ — see Task 2a Step 2.

- [ ] **Step 3: Verify and commit**

```bash
./mvnw -B verify
git add -A && git commit -m "feat: implement the warehouse repository adapter. refs #3"
```

### Task 2f: Contract, REST adapter and mappers

**Files:** `warehouse-openapi.yaml`, `WarehouseResourceImpl.java`, two mapper classes.

- [ ] **Step 1: Contract first — add 409 and 422 to `POST /warehouse`**

Under `paths: /warehouse: post: responses:`, after the existing `'400'`:

```yaml
        '409':
          description: A warehouse with that business unit code already exists
        '422':
          description: Id was invalidly set on the request
```

- [ ] **Step 2: Write the exception mappers**

```java
// WarehouseExceptionMapper.java
@Provider
public class WarehouseExceptionMapper implements ExceptionMapper<WarehouseDomainException> {

  @Inject ObjectMapper objectMapper;

  @Override
  public Response toResponse(WarehouseDomainException exception) {
    int code = statusFor(exception);
    ObjectNode body = objectMapper.createObjectNode();
    body.put("exceptionType", exception.getClass().getName());
    body.put("code", code);
    body.put("error", exception.getMessage());
    return Response.status(code).entity(body).build();
  }

  private static int statusFor(WarehouseDomainException exception) {
    if (exception instanceof WarehouseNotFoundException) return 404;
    if (exception instanceof DuplicateBusinessUnitCodeException) return 409;
    if (exception instanceof ClientSuppliedIdException) return 422;
    return 400;
  }
}
```

The body shape mirrors the existing `ErrorMapper` in `ProductResource`/`StoreResource` so the API
stays internally consistent. `ValidationExceptionMapper` does the same for bean-validation failures
→ 400, otherwise Quarkus emits its own `ViolationReport` shape (§5).

The type to catch is `io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException`
— **not** anything under `org.jboss.resteasy.reactive.*`, which is the natural guess and does not
compile.

- [ ] **Step 3: Implement the resource**

```java
@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject WarehouseStore warehouseStore;              // the port, not the repository
  @Inject CreateWarehouseOperation createWarehouse;
  @Inject ReplaceWarehouseOperation replaceWarehouse;
  @Inject ArchiveWarehouseOperation archiveWarehouse;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseStore.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  /**
   * The @POST is not redundant. @ResponseStatus is read only from a method that carries its own
   * JAX-RS method annotation; on a method whose HTTP metadata is inherited from the generated
   * interface it is silently ignored and the response is 200. Verified against a running app:
   * without @POST the contract's 201 never appears, with no error anywhere.
   */
  @Override
  @POST
  @ResponseStatus(201)
  public Warehouse createANewWarehouseUnit(Warehouse data) {
    var warehouse = toDomain(data);
    createWarehouse.create(warehouse);
    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    return toWarehouseResponse(requireActive(id));
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    archiveWarehouse.archive(requireActive(id));
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(String businessUnitCode, Warehouse data) {
    if (data.getBusinessUnitCode() != null
        && !businessUnitCode.equals(data.getBusinessUnitCode())) {
      throw new InvalidWarehouseDataException(
          "Business unit code in the path and the body must match.");
    }
    var warehouse = toDomain(data);
    warehouse.businessUnitCode = businessUnitCode;   // the path is authoritative — decision 9
    replaceWarehouse.replace(warehouse);
    return toWarehouseResponse(warehouse);
  }

  /** A malformed id is indistinguishable from a missing one to a caller — decision 3. */
  private com.fulfilment.…domain.models.Warehouse requireActive(String id) {
    long numericId;
    try {
      numericId = Long.parseLong(id);
    } catch (NumberFormatException e) {
      throw new WarehouseNotFoundException("Warehouse " + id + " does not exist.");
    }
    var warehouse = warehouseStore.findActiveById(numericId);
    if (warehouse == null) {
      throw new WarehouseNotFoundException("Warehouse " + id + " does not exist.");
    }
    return warehouse;
  }
}
```

`@ResponseStatus` is `org.jboss.resteasy.reactive.ResponseStatus`; `@POST` is `jakarta.ws.rs.POST`.

**Both mapping directions must be written out.** The baseline only has the outbound one, and it drops
the id:

```java
  /** Domain -> API. The baseline version never set id, so the API could not return it (defect 2.2.8). */
  private Warehouse toWarehouseResponse(com.fulfilment.…domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setId(warehouse.id == null ? null : String.valueOf(warehouse.id));   // decision 10
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    return response;
  }

  /**
   * API -> domain. The id MUST be carried across: CreateWarehouseUseCase.validate keys the 422 rule
   * off warehouse.id != null, so a mapper that ignores the inbound id makes that rule unreachable
   * and its test unpassable.
   *
   * <p>A non-numeric id on create is still a client-supplied id — 422, not 404. Decision 3's 404
   * covers path parameters, where a malformed id is indistinguishable from a missing resource; here
   * the client has supplied something it should not have supplied at all.
   */
  private com.fulfilment.…domain.models.Warehouse toDomain(Warehouse data) {
    var warehouse = new com.fulfilment.…domain.models.Warehouse();
    if (data.getId() != null && !data.getId().isBlank()) {
      try {
        warehouse.id = Long.parseLong(data.getId().trim());
      } catch (NumberFormatException e) {
        throw new ClientSuppliedIdException();
      }
    }
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }
```

- [ ] **Step 4: Write the REST tests** — `WarehouseEndpointTest` (`@QuarkusTest`)

Cover: list excludes archived; create returns **201** with an id; duplicate code → **409**; unknown location → **400**; client-supplied id → **422**; unknown id → **404**; non-numeric id → **404**; archive returns **204** then the warehouse disappears from list and get; replace returns **200** and reuses the code.

Include the gate that proves the two validator fixes did not cancel out:

```java
  @Test
  void postingANullBodyIsRejectedWithFourHundred() {
    given().contentType("application/json").body("null")
        .when().post("warehouse")
        .then().statusCode(400);
  }
```

- [ ] **Step 5: Rewrite the integration test**

Uncomment `testSimpleCheckingArchivingWarehouses`, add `import static org.hamcrest.core.IsNot.not;`, and rewrite it to **create and archive its own warehouse** rather than mutating seed row 1, asserting on the **business unit code** — never on a location string, which is shared between warehouses (§8).

- [ ] **Step 6: Verify, commit, PR**

```bash
./mvnw -B verify
git add -A && git commit -m "feat: expose the warehouse API. refs #3"
git push -u origin feat/3-warehouse-operations && gh pr create --fill
```

---

## Task 3: Post-commit store sync — refs #2

**Files:**
- Create: `…/stores/StoreChangedEvent.java`, `…/stores/LegacyStoreSyncObserver.java`
- Create (test): `…/stores/RecordingLegacyStoreManagerGateway.java`, `…/stores/StoreLegacySyncTest.java`
- Modify: `…/stores/StoreResource.java`
- **Do not touch** `LegacyStoreManagerGateway.java`

```bash
git switch -c feat/2-store-legacy-sync main
```

- [ ] **Step 1: The event**

```java
package com.fulfilment.application.monolith.stores;

/**
 * Immutable snapshot of a committed store change.
 *
 * <p>A snapshot rather than the entity: observers run after the transaction ends, when the
 * persistence context is gone, so reading a managed entity there is reading a detached instance.
 * Capturing the values inside the transaction guarantees the legacy system receives what was
 * actually committed.
 */
public record StoreChangedEvent(
    Long id, String name, int quantityProductsInStock, Operation operation) {

  /** No DELETE: the legacy gateway exposes no delete operation, so it would be dead code. */
  public enum Operation {
    CREATED,
    UPDATED
  }

  public static StoreChangedEvent of(Store store, Operation operation) {
    return new StoreChangedEvent(store.id, store.name, store.quantityProductsInStock, operation);
  }
}
```

- [ ] **Step 2: The observer**

```java
@ApplicationScoped
public class LegacyStoreSyncObserver {

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  /**
   * Fires only after the transaction commits, so a rollback propagates nothing.
   *
   * <p>Rebuilds a transient Store because the provided gateway's signature takes one and stays
   * byte-identical to the baseline.
   */
  void onStoreChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreChangedEvent event) {
    Store store = new Store(event.name());
    store.id = event.id();
    store.quantityProductsInStock = event.quantityProductsInStock();

    switch (event.operation()) {
      case CREATED -> legacyStoreManagerGateway.createStoreOnLegacySystem(store);
      case UPDATED -> legacyStoreManagerGateway.updateStoreOnLegacySystem(store);
    }
  }
}
```

- [ ] **Step 3: Write the failing tests first**

`RecordingLegacyStoreManagerGateway` is an `@Alternative @Priority(1)` subclass of the provided
gateway, capturing calls into a static list and overriding both methods so nothing touches the
filesystem:

```java
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingLegacyStoreManagerGateway extends LegacyStoreManagerGateway {

  public record Call(String operation, Long id, String name, int stock) {}

  public static final List<Call> CALLS = new CopyOnWriteArrayList<>();

  public static void reset() {
    CALLS.clear();
  }

  @Override
  public void createStoreOnLegacySystem(Store store) {
    CALLS.add(new Call("CREATE", store.id, store.name, store.quantityProductsInStock));
  }

  @Override
  public void updateStoreOnLegacySystem(Store store) {
    CALLS.add(new Call("UPDATE", store.id, store.name, store.quantityProductsInStock));
  }
}
```

Then the tests (`@QuarkusTest`, `@BeforeEach` calling `reset()`):

```java
  @Test
  void aCommittedCreateNotifiesTheLegacySystemOnceWithAnId() {
    given().contentType("application/json").body("{\"name\":\"SYNC-OK\",\"quantityProductsInStock\":3}")
        .when().post("store").then().statusCode(201);

    assertEquals(1, CALLS.size());
    assertEquals("CREATE", CALLS.get(0).operation());
    assertNotNull(CALLS.get(0).id(), "the legacy system must receive a persisted id (defect 2.2.1)");
  }

  @Test
  void aRolledBackTransactionNotifiesNothing() {
    // Store.name is @Column(unique = true), so re-using an existing name makes the commit fail.
    // The gateway call must never happen, which is the entire point of Task 2.
    given().contentType("application/json").body("{\"name\":\"TONSTAD\",\"quantityProductsInStock\":1}")
        .when().post("store").then().statusCode(greaterThanOrEqualTo(400));

    assertTrue(CALLS.isEmpty(), "a rolled-back transaction must notify nothing");
  }

  @Test
  void patchingOnlyTheNameLeavesStockUnchanged() {
    // KALLAX is seeded with stock 5
    given().contentType("application/json").body("{\"name\":\"KALLAX-RENAMED\"}")
        .when().patch("store/2").then().statusCode(200)
        .body("quantityProductsInStock", equalTo(5));
  }

  @Test
  void patchingStockToZeroActuallySetsZero() {
    // The discriminating case: every "skip if zero" implementation silently fails here.
    given().contentType("application/json").body("{\"quantityProductsInStock\":0}")
        .when().patch("store/3").then().statusCode(200)
        .body("quantityProductsInStock", equalTo(0));
  }

  @Test
  void patchingWithAPresentButBlankNameIsRejected() {
    given().contentType("application/json").body("{\"name\":\"\"}")
        .when().patch("store/2").then().statusCode(422);
  }

  @Test
  void patchingWithAnAbsentBodyIsRejected() {
    given().contentType("application/json").body("null")
        .when().patch("store/2").then().statusCode(400);
  }
```

The last two encode the PATCH rules from §6: a name key that is *present* but blank or null is 422,
an omitted name is simply not applied, and a null or absent body is 400.

Because the seeded rows are shared, use a distinct store per test where one mutates state.

- [ ] **Step 4: Rewrite `StoreResource`**

Inject `Event<StoreChangedEvent>`; fire `CREATED` in `create` and `UPDATED` in `update`/`patch`, built from the **persisted entity** (fixing defect 2.2.1). Replace the PATCH signature with `ObjectNode` and apply fields only when `has(...)` reports them present — the absent-versus-zero distinction is available on the JSON tree and nowhere else. `Store.quantityProductsInStock` stays a primitive `int`. `delete` is left as-is; the scope decision is documented in `QUESTIONS.md`, not worked around here.

- [ ] **Step 5: Verify, commit, PR**

```bash
./mvnw -B verify
git add -A && git commit -m "feat: notify the legacy system only after commit. refs #2"
git push -u origin feat/2-store-legacy-sync && gh pr create --fill
```

- [ ] **Step 6: Confirm the gateway is untouched**

```bash
git diff baseline..HEAD -- '*LegacyStoreManagerGateway.java'   # must be empty
```

---

## Task 4: Fulfilment associations (bonus) — refs #4

```bash
git switch -c feat/4-fulfilment-associations main
```

**Files:** create `…/fulfilment/{FulfilmentAssociation,FulfilmentAssociationRepository,FulfilmentService,FulfilmentResource,FulfilmentRequest}.java` plus exceptions and tests.

- [ ] **Step 1: Entity** — `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id","product_id","warehouse_id"}))`, `@ManyToOne` to `Store`, `Product`, `DbWarehouse`.

- [ ] **Step 2: Write the failing service tests** — one per rule, each at the boundary:

- 2 warehouses for a product in a store succeeds; the 3rd → 400
- 3 distinct warehouses for a store succeeds; a 4th → 400
- 5 distinct products in a warehouse succeeds; a 6th → 400
- the same triple twice → 409
- an archived warehouse → 404
- unknown store / product / warehouse → 404

- [ ] **Step 3: Implement the service**

`FulfilmentService` is `@ApplicationScoped` and `@Transactional`, injecting
`FulfilmentAssociationRepository`, `WarehouseStore`, `StoreRepository`-equivalent access and
`ProductRepository`. `associate(storeId, productId, warehouseBusinessUnitCode)` in order:

1. Resolve the store, product and warehouse. Any missing → `FulfilmentNotFoundException` (404)
   naming which one. The warehouse is resolved through `WarehouseStore.findByBusinessUnitCode`,
   which already excludes archived rows — so an archived warehouse is a 404 for free, and the rule
   from decision 11 needs no separate check.
2. Reject an existing identical triple → 409.
3. Count and enforce, each → 400 with a message naming the limit:
   - warehouses already fulfilling this product in this store, `>= 2` → reject
   - distinct warehouses already fulfilling this store, `>= 3` **and** the new warehouse is not
     already among them → reject
   - distinct products already in this warehouse, `>= 5` **and** the new product is not already
     among them → reject
4. Persist.

The "and not already among them" qualifiers matter: adding a second product to a warehouse a store
already uses must not count against the store's warehouse limit, because the warehouse count has not
changed. Getting this wrong makes rule 2 reject legitimate requests, and the boundary tests in
Step 2 are what catch it.

- [ ] **Step 4: Implement the resource** — `POST /fulfilment` taking a `FulfilmentRequest` record,
`GET /fulfilment?storeId=`, `DELETE /fulfilment/{id}` → 204. A `FulfilmentExceptionMapper` mirroring
the warehouse one keeps the error shape consistent.

Hand-coded rather than generated from a contract: deliberate, and argued in the Question 2 answer
(ADR-0008).

- [ ] **Step 5: Verify, commit, PR**

```bash
./mvnw -B verify
git add -A && git commit -m "feat: associate warehouses as fulfilment units. refs #4"
git push -u origin feat/4-fulfilment-associations && gh pr create --fill
```

---

## Task 5: Mutation testing — refs #5

```bash
git switch -c chore/5-mutation-testing main
```

- [ ] **Step 1: Add a `mutation` profile to `pom.xml`**

```xml
<profile>
    <id>mutation</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.19.1</version>
                <dependencies>
                    <dependency>
                        <groupId>org.pitest</groupId>
                        <artifactId>pitest-junit5-plugin</artifactId>
                        <version>1.2.2</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <targetClasses>
                        <param>com.fulfilment.application.monolith.warehouses.domain.*</param>
                        <param>com.fulfilment.application.monolith.location.*</param>
                    </targetClasses>
                    <targetTests>
                        <param>com.fulfilment.application.monolith.warehouses.domain.usecases.*Test</param>
                        <param>com.fulfilment.application.monolith.location.*Test</param>
                    </targetTests>
                    <excludedTestClasses>
                        <param>*QuarkusTest</param>
                        <param>*IT</param>
                    </excludedTestClasses>
                    <mutationThreshold>85</mutationThreshold>
                    <timestampedReports>false</timestampedReports>
                    <outputFormats>
                        <param>HTML</param>
                        <param>XML</param>
                    </outputFormats>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Scoped to the domain: PIT rewrites bytecode and so does Quarkus, so aiming it at `@QuarkusTest` classes is unreliable (ADR-0007). The domain is also where the rules live.

- [ ] **Step 2: Run it**

```bash
./mvnw -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
open target/pit-reports/index.html
```

- [ ] **Step 3: Triage every survivor**

For each surviving mutant, exactly one of:
- **Strengthen the test** until it kills the mutant — the usual outcome, and it means a rule was under-asserted.
- **Delete the test** if it turns out to assert nothing of value. This is the point of the exercise.
- **Record it** in the ADR if the mutant is genuinely equivalent (e.g. a `LocalDateTime.now()` boundary).

Do not lower `mutationThreshold` to make the build pass. If time runs out, record the achieved score honestly — a real 71% with an explanation beats a manufactured 85%.

- [ ] **Step 4: Record the triage in `docs/AI_COLLABORATION.md`**

The file already exists and already carries the workflow and the corrections. Add the achieved
mutation score, how many survivors there were, and — specifically — any test that was **deleted**
because a surviving mutant proved it asserted nothing. That deletion is the visible payoff of the
whole technique, and it is worth naming rather than leaving implicit in the diff.

- [ ] **Step 5: Commit and PR**

```bash
git add -A && git commit -m "test: add mutation testing and triage survivors. refs #5"
git push -u origin chore/5-mutation-testing && gh pr create --fill
```

---

## Task 6: CI, docs and the written deliverables — refs #5

```bash
git switch -c docs/5-ci-and-deliverables main
```

- [ ] **Step 1: `.github/workflows/ci.yml`**

```yaml
name: CI
on:
  push: { branches: [main] }
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_USER: quarkus_test
          POSTGRES_PASSWORD: quarkus_test
          POSTGRES_DB: quarkus_test
        ports: ['15432:5432']
        options: >-
          --health-cmd "pg_isready -U quarkus_test"
          --health-interval 5s --health-timeout 5s --health-retries 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - name: Build and test
        working-directory: java-assignment
        run: ./mvnw -B verify
      - name: Assert the integration test actually ran
        working-directory: java-assignment
        run: |
          test -f target/failsafe-reports/TEST-com.fulfilment.application.monolith.warehouses.adapters.restapi.WarehouseEndpointIT.xml \
            || { echo "WarehouseEndpointIT did not run"; exit 1; }

  mutation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - name: Mutation coverage
        working-directory: java-assignment
        run: ./mvnw -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: pit-report
          path: java-assignment/target/pit-reports/
```

The explicit report check exists because a green build that silently skipped the IT is exactly defect 2.2.11. The mutation job needs no database — the domain tests are plain JUnit.

- [ ] **Step 2: ADRs 0001–0010** in `docs/adr/`, one short file each, per the spec's §9 register. Each states context, decision, alternatives, consequences. ADR-0005 must record that the generator swap was recommended and rejected, and that `x-codegen-annotations` was proven unworkable by experiment.

- [ ] **Step 3: Rewrite the root `README.md`** — what this is, how to run it (`docker compose up -d && cd java-assignment && ./mvnw verify`), architecture, the defects found in the provided code, the decision index, and the AI-collaboration pointer. Fix the broken link: `assignment/CODE_ASSIGNMENT.md` → `java-assignment/CODE_ASSIGNMENT.md`.

- [ ] **Step 4: Answer `java-assignment/QUESTIONS.md`** in the existing ```txt blocks. Reasoning and this codebase only — **no personal or biographical claims** (§10). Q1: refactor by risk, not symmetry, plus the migration recommendation. Q2: the generator investigation first-hand, the contract-before-code 409/422 change, and the deliberate hand-coding of the bonus. Q3: the pyramid, and mutation score over line coverage. Q2 also carries the delete-propagation scope decision, verbatim from §10.

- [ ] **Step 5: Answer `case-study/CASE_STUDY.md`** — five scenarios, reasoning plus explicit discovery questions. Scenario 1 must visibly substitute reasoning for the experience it asks for rather than appear to skip the prompt. Scenario 5 answers directly from this implementation: archive rather than delete, business unit code reuse, effective dating, transactional replacement.

- [ ] **Step 6: Final verification**

```bash
docker compose up -d
cd java-assignment && ./mvnw -B clean verify
cd .. && git diff baseline..HEAD --stat
grep -rn "UnsupportedOperationException" java-assignment/src/main/   # expect no matches
```

- [ ] **Step 7: Commit, PR, close the issues**

```bash
git add -A && git commit -m "docs: add CI, decision records and written answers. refs #5"
git push -u origin docs/5-ci-and-deliverables && gh pr create --fill
```

---

## Definition of done

- [ ] No `UnsupportedOperationException` remains in `src/main`
- [ ] `./mvnw verify` green, with the IT confirmed to have run
- [ ] Mutation score recorded (whatever it is) and survivors triaged
- [ ] `git diff baseline..HEAD` shows the untouchable files unchanged
- [ ] `QUESTIONS.md` and `CASE_STUDY.md` answered, no personal claims
- [ ] All five issues closed via merged PRs
- [ ] CI green on `main`
