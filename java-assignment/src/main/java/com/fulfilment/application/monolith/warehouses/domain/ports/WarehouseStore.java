package com.fulfilment.application.monolith.warehouses.domain.ports;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.util.List;

public interface WarehouseStore {

  List<Warehouse> getAll();

  void create(Warehouse warehouse);

  void update(Warehouse warehouse);

  void remove(Warehouse warehouse);

  Warehouse findByBusinessUnitCode(String buCode);

  /**
   * The active warehouse with this database id, or null.
   *
   * <p>Deliberately NOT named findById: WarehouseRepository also implements
   * PanacheRepository&lt;DbWarehouse&gt;, which declares findById(Long) returning DbWarehouse. Two
   * methods with the same erasure and incompatible return types is a hard compile error —
   * "findById(Long) in WarehouseRepository cannot implement findById(Id) in PanacheRepositoryBase".
   * The name also says the thing that matters: archived rows are never returned.
   */
  Warehouse findActiveById(Long id);
}
