package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/** Base type for warehouse rule violations, so the REST adapter maps one hierarchy. */
public abstract class WarehouseDomainException extends RuntimeException {

  protected WarehouseDomainException(String message) {
    super(message);
  }
}
