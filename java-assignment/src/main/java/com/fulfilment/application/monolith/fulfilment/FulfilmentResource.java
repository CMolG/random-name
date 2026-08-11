package com.fulfilment.application.monolith.fulfilment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Hand-written rather than generated from a contract, unlike the warehouse API. The reasoning is in
 * the Question 2 answer: the generator earns its keep where an agreed contract already exists, and
 * costs more than it returns for an endpoint being designed here for the first time.
 */
@Path("fulfilment")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FulfilmentResource {

  @Inject FulfilmentService fulfilmentService;

  @GET
  public List<FulfilmentResponse> list(@QueryParam("storeId") Long storeId) {
    var associations =
        storeId == null ? fulfilmentService.findAll() : fulfilmentService.findByStore(storeId);
    return associations.stream().map(FulfilmentResponse::of).toList();
  }

  @POST
  public Response associate(@Valid @NotNull FulfilmentRequest request) {
    var association =
        fulfilmentService.associate(
            request.storeId(), request.productId(), request.warehouseBusinessUnitCode());

    return Response.status(201).entity(FulfilmentResponse.of(association)).build();
  }

  @DELETE
  @Path("{id}")
  public Response remove(@PathParam("id") Long id) {
    fulfilmentService.remove(id);
    return Response.status(204).build();
  }
}
