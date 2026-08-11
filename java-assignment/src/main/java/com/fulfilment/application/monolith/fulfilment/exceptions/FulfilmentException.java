package com.fulfilment.application.monolith.fulfilment.exceptions;

/** Base type for fulfilment rule violations, so the REST adapter maps one hierarchy. */
public abstract class FulfilmentException extends RuntimeException {

  protected FulfilmentException(String message) {
    super(message);
  }
}
