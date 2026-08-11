package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
