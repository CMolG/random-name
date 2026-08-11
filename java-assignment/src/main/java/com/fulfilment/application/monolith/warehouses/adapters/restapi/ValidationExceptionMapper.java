package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.stream.Collectors;

/**
 * Bean-validation failures — the contract's {@code @NotNull} body, for instance — as 400 in the
 * house error shape. Without this Quarkus emits its own ViolationReport, which is a second error
 * format for clients to special-case.
 *
 * <p>The type to catch is Quarkus's ResteasyReactiveViolationException, not anything under
 * org.jboss.resteasy.reactive: the RESTEasy types are not what the Quarkus validator throws.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ResteasyReactiveViolationException> {

  @Inject ObjectMapper objectMapper;

  @Override
  public Response toResponse(ResteasyReactiveViolationException exception) {
    String message =
        exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .collect(Collectors.joining("; "));

    ObjectNode body = objectMapper.createObjectNode();
    body.put("exceptionType", exception.getClass().getName());
    body.put("code", 400);
    body.put("error", message.isBlank() ? "Invalid request parameters." : message);

    return Response.status(400).entity(body).build();
  }
}
