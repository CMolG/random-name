package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** A field or business rule was violated — mapped to 400. */
public class InvalidWarehouseDataException extends WarehouseDomainException {

  public InvalidWarehouseDataException(String message) {
    super(message);
  }
}
