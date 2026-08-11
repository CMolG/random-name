package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
