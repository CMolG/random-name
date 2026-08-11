package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.ClientSuppliedIdException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DuplicateBusinessUnitCodeException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.InvalidWarehouseDataException;
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

  private static Warehouse warehouse(
      String buCode, String location, Integer capacity, Integer stock) {
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
    var thrown =
        assertThrows(
            InvalidWarehouseDataException.class,
            () -> useCase.create(warehouse("MWH.100", "ATLANTIS-001", 10, 1)));

    assertTrue(thrown.getMessage().contains("does not exist"), thrown.getMessage());
  }

  /**
   * A missing location and an unknown one are different client mistakes and must not collapse into
   * one message: without its own check, a blank location reaches the resolver and is reported as
   * "location    does not exist", which tells the caller nothing useful.
   */
  @Test
  void reportsAMissingLocationDifferentlyFromAnUnknownOne() {
    var thrown =
        assertThrows(
            InvalidWarehouseDataException.class,
            () -> useCase.create(warehouse("MWH.100", "   ", 10, 1)));

    assertTrue(thrown.getMessage().contains("must be provided"), thrown.getMessage());
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

    var thrown =
        assertThrows(
            InvalidWarehouseDataException.class,
            () -> useCase.create(warehouse("MWH.301", "AMSTERDAM-001", 11, 1)));

    // 100 max - 90 used = 10 remaining; the figure the client is told has to be the remainder
    assertTrue(thrown.getMessage().contains("remaining capacity of 10"), thrown.getMessage());
  }

  @Test
  void acceptsCapacityThatExactlyFillsTheLocation() {
    store.given("MWH.300", "AMSTERDAM-001", 90, 10);

    useCase.create(warehouse("MWH.301", "AMSTERDAM-001", 10, 1));

    assertEquals(2, store.getAll().size());
  }

  /** A brand-new warehouse holds nothing. Zero stock is the ordinary case, not an error. */
  @Test
  void acceptsAWarehouseWithZeroStock() {
    useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, 0));

    assertEquals(1, store.getAll().size());
  }

  /** A full warehouse is legal; only stock beyond capacity is not. */
  @Test
  void acceptsStockThatExactlyEqualsCapacity() {
    useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, 10));

    assertEquals(1, store.getAll().size());
  }

  /** Location limits are per location: warehouses elsewhere must not count towards them. */
  @Test
  void warehousesAtOtherLocationsDoNotCountTowardsALocationLimit() {
    store.given("MWH.900", "TILBURG-001", 30, 5);

    // HELMOND-001 allows exactly 1 warehouse of at most 45 capacity, and holds none
    useCase.create(warehouse("MWH.901", "HELMOND-001", 45, 5));

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
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse(null, "AMSTERDAM-001", 10, 1)));
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("  ", "AMSTERDAM-001", 10, 1)));
    assertThrows(
        InvalidWarehouseDataException.class, () -> useCase.create(warehouse("MWH.100", null, 10, 1)));
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", null, 1)));
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 0, 0)));
    assertThrows(
        InvalidWarehouseDataException.class,
        () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-001", 10, null)));
    assertThrows(
        InvalidWarehouseDataException.class,
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
