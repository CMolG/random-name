package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.ClientSuppliedIdException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.DuplicateBusinessUnitCodeException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseDomainException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates domain failures into HTTP so the use cases never mention status codes.
 *
 * <p>The body shape mirrors the ErrorMapper the provided ProductResource and StoreResource already
 * use, so a client sees one error format across the whole API.
 */
@Provider
public class WarehouseExceptionMapper implements ExceptionMapper<WarehouseDomainException> {

  @Inject ObjectMapper objectMapper;

  @Override
  public Response toResponse(WarehouseDomainException exception) {
    int code = statusFor(exception);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("exceptionType", exception.getClass().getName());
    body.put("code", code);
    body.put("error", exception.getMessage());

    return Response.status(code).entity(body).build();
  }

  private static int statusFor(WarehouseDomainException exception) {
    if (exception instanceof WarehouseNotFoundException) {
      return 404;
    }
    if (exception instanceof DuplicateBusinessUnitCodeException) {
      return 409;
    }
    if (exception instanceof ClientSuppliedIdException) {
      return 422;
    }
    return 400;
  }
}
