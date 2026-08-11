package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

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

  /**
   * Named findActiveById rather than findById because Panache already contributes
   * findById(Long) returning DbWarehouse; two methods with that erasure and different return types
   * do not compile. {@code update} above calls the Panache one.
   */
  @Override
  public Warehouse findActiveById(Long id) {
    DbWarehouse db = find("id = ?1 and archivedAt is null", id).firstResult();
    return db == null ? null : db.toWarehouse();
  }
}
