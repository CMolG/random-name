package com.fulfilment.application.monolith.fulfilment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fulfilment.application.monolith.fulfilment.exceptions.DuplicateFulfilmentException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentNotFoundException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Mirrors the warehouse mapper, so the whole API reports errors in one shape. */
@Provider
public class FulfilmentExceptionMapper implements ExceptionMapper<FulfilmentException> {

  @Inject ObjectMapper objectMapper;

  @Override
  public Response toResponse(FulfilmentException exception) {
    int code = statusFor(exception);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("exceptionType", exception.getClass().getName());
    body.put("code", code);
    body.put("error", exception.getMessage());

    return Response.status(code).entity(body).build();
  }

  private static int statusFor(FulfilmentException exception) {
    if (exception instanceof FulfilmentNotFoundException) {
      return 404;
    }
    if (exception instanceof DuplicateFulfilmentException) {
      return 409;
    }
    return 400;
  }
}
