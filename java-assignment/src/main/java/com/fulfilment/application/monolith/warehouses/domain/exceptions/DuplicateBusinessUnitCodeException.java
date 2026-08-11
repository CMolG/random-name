package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** The business unit code is already taken by an active warehouse — mapped to 409. */
public class DuplicateBusinessUnitCodeException extends WarehouseDomainException {

  public DuplicateBusinessUnitCodeException(String businessUnitCode) {
    super("An active warehouse with business unit code " + businessUnitCode + " already exists.");
  }
}
