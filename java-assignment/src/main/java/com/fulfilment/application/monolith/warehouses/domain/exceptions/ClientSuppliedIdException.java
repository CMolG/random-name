package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/**
 * The client supplied an id the server owns — mapped to 422, matching the wording and status the
 * provided StoreResource already uses for the same mistake.
 */
public class ClientSuppliedIdException extends WarehouseDomainException {

  public ClientSuppliedIdException() {
    super("Id was invalidly set on request.");
  }
}
