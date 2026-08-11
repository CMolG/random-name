package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.InvalidWarehouseDataException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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
   *
   * <p>{@code @Transactional} makes the archive-plus-create pair atomic, so a successor rejected by
   * a creation rule rolls the archival back rather than stranding the business unit code.
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
