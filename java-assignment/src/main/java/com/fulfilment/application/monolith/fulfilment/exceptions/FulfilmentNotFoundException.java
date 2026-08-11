package com.fulfilment.application.monolith.fulfilment.exceptions;

/**
 * A store, product, warehouse or association named by the request does not exist — mapped to 404.
 *
 * <p>An archived warehouse lands here too: it is resolved through the warehouse port, which never
 * returns archived rows, so "archived" and "absent" are the same answer to a caller.
 */
public class FulfilmentNotFoundException extends FulfilmentException {

  public FulfilmentNotFoundException(String message) {
    super(message);
  }
}
