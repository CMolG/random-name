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
