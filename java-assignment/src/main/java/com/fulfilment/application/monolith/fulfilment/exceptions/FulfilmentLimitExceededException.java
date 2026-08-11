package com.fulfilment.application.monolith.fulfilment.exceptions;

/** One of the three fulfilment limits would be exceeded — mapped to 400. */
public class FulfilmentLimitExceededException extends FulfilmentException {

  public FulfilmentLimitExceededException(String message) {
    super(message);
  }
}
