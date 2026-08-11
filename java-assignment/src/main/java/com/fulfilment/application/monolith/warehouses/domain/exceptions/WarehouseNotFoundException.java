package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** No active warehouse matches the request — mapped to 404. */
public class WarehouseNotFoundException extends WarehouseDomainException {

  public WarehouseNotFoundException(String message) {
    super(message);
  }
}
