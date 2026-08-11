package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.ClientSuppliedIdException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DuplicateBusinessUnitCodeException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.InvalidWarehouseDataException;
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
          "Warehouse stock "
              + warehouse.stock
              + " exceeds its capacity "
              + warehouse.capacity
              + ".");
    }
    if (warehouseStore.findByBusinessUnitCode(warehouse.businessUnitCode) != null) {
      throw new DuplicateBusinessUnitCodeException(warehouse.businessUnitCode);
    }

    Location location = locationResolver.resolveByIdentifier(warehouse.location);
    if (location == null) {
      throw new InvalidWarehouseDataException("Location " + warehouse.location + " does not exist.");
    }

    var atLocation =
        warehouseStore.getAll().stream()
            .filter(w -> location.identification.equals(w.location))
            .toList();

    if (atLocation.size() >= location.maxNumberOfWarehouses) {
      throw new InvalidWarehouseDataException(
          "Location "
              + location.identification
              + " already holds its maximum of "
              + location.maxNumberOfWarehouses
              + " warehouses.");
    }

    int used = atLocation.stream().mapToInt(w -> w.capacity).sum();
    if (used + warehouse.capacity > location.maxCapacity) {
      throw new InvalidWarehouseDataException(
          "Capacity "
              + warehouse.capacity
              + " exceeds the remaining capacity of "
              + (location.maxCapacity - used)
              + " at "
              + location.identification
              + ".");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidWarehouseDataException("Warehouse " + field + " must be provided.");
    }
  }
}
