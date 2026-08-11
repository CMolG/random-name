package com.fulfilment.application.monolith.fulfilment.exceptions;

/** The store/product/warehouse triple is already associated — mapped to 409. */
public class DuplicateFulfilmentException extends FulfilmentException {

  public DuplicateFulfilmentException(String message) {
    super(message);
  }
}
