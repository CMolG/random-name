package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.InvalidWarehouseDataException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
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

  /** Accommodating the stock means holding it exactly, not strictly exceeding it. */
  @Test
  void successorCapacityMayEqualThePredecessorStock() {
    store.given("MWH.100", "AMSTERDAM-001", 50, 30);

    useCase.replace(successor("MWH.100", "AMSTERDAM-001", 30, 30));

    assertEquals(30, store.findByBusinessUnitCode("MWH.100").capacity);
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
