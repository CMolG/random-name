package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.ClientSuppliedIdException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.InvalidWarehouseDataException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import java.util.List;
import org.jboss.resteasy.reactive.ResponseStatus;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  // the ports, not the repository: the adapter depends on the domain's contract, not on Panache
  @Inject WarehouseStore warehouseStore;
  @Inject CreateWarehouseOperation createWarehouse;
  @Inject ReplaceWarehouseOperation replaceWarehouse;
  @Inject ArchiveWarehouseOperation archiveWarehouse;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseStore.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  /**
   * The @POST is not redundant. @ResponseStatus is read only from a method that carries its own
   * JAX-RS method annotation; on a method whose HTTP metadata is inherited from the generated
   * interface it is silently ignored and the response is 200. Verified against a running app:
   * without @POST the contract's 201 never appears, with no error anywhere.
   */
  @Override
  @POST
  @ResponseStatus(201)
  public Warehouse createANewWarehouseUnit(Warehouse data) {
    var warehouse = toDomain(data);
    createWarehouse.create(warehouse);
    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    return toWarehouseResponse(requireActive(id));
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    archiveWarehouse.archive(requireActive(id));
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(String businessUnitCode, Warehouse data) {
    if (data.getBusinessUnitCode() != null && !businessUnitCode.equals(data.getBusinessUnitCode())) {
      throw new InvalidWarehouseDataException(
          "Business unit code in the path and the body must match.");
    }
    var warehouse = toDomain(data);
    warehouse.businessUnitCode = businessUnitCode; // the path is authoritative
    replaceWarehouse.replace(warehouse);
    return toWarehouseResponse(warehouse);
  }

  /** A malformed id is indistinguishable from a missing one to a caller, so both are 404. */
  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse requireActive(
      String id) {
    long numericId;
    try {
      numericId = Long.parseLong(id);
    } catch (NumberFormatException e) {
      throw new WarehouseNotFoundException("Warehouse " + id + " does not exist.");
    }

    var warehouse = warehouseStore.findActiveById(numericId);
    if (warehouse == null) {
      throw new WarehouseNotFoundException("Warehouse " + id + " does not exist.");
    }
    return warehouse;
  }

  /** Domain -> API. The baseline version never set id, so the API could not return it. */
  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setId(warehouse.id == null ? null : String.valueOf(warehouse.id));
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }

  /**
   * API -> domain. The id MUST be carried across: CreateWarehouseUseCase.validate keys the 422 rule
   * off warehouse.id != null, so a mapper that ignores the inbound id makes that rule unreachable
   * and its test unpassable.
   *
   * <p>A non-numeric id on create is still a client-supplied id — 422, not 404. The 404 for
   * malformed ids covers path parameters, where a malformed id is indistinguishable from a missing
   * resource; here the client has supplied something it should not have supplied at all.
   */
  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomain(
      Warehouse data) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    if (data.getId() != null && !data.getId().isBlank()) {
      try {
        warehouse.id = Long.parseLong(data.getId().trim());
      } catch (NumberFormatException e) {
        throw new ClientSuppliedIdException();
      }
    }
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }
}
