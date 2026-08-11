package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  /**
   * Stamps {@code archivedAt} and persists the change through {@code update}, not {@code remove}:
   * archival is a field mutation on an existing row, and the row is history worth keeping.
   */
  @Override
  public void archive(Warehouse warehouse) {
    if (warehouse == null || warehouse.archivedAt != null) {
      throw new WarehouseNotFoundException("No active warehouse to archive.");
    }
    warehouse.archivedAt = LocalDateTime.now();
    warehouseStore.update(warehouse);
  }
}
