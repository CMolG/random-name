package com.fulfilment.application.monolith.stores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.jboss.logging.Logger;

@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

  /**
   * Store changes reach the legacy system through an event observed after the commit, never by
   * calling the gateway from inside the transaction. See {@link LegacyStoreSyncObserver}.
   */
  @Inject Event<StoreChangedEvent> storeChanged;

  private static final Logger LOGGER = Logger.getLogger(StoreResource.class.getName());

  @GET
  public List<Store> get() {
    return Store.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Store getSingle(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    return entity;
  }

  @POST
  @Transactional
  public Response create(Store store) {
    if (store == null) {
      throw new WebApplicationException("Store was not set on request.", 400);
    }
    if (store.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    store.persist();

    // fired from the persisted entity, so the legacy system receives the generated id
    storeChanged.fire(StoreChangedEvent.of(store, StoreChangedEvent.Operation.CREATED));

    return Response.ok(store).status(201).build();
  }

  @PUT
  @Path("{id}")
  @Transactional
  public Store update(Long id, Store updatedStore) {
    if (updatedStore == null || updatedStore.name == null) {
      throw new WebApplicationException("Store Name was not set on request.", 422);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;

    // the entity, not the request body: the body carries no id, so the legacy system was being
    // told about a store it could not identify
    storeChanged.fire(StoreChangedEvent.of(entity, StoreChangedEvent.Operation.UPDATED));

    return entity;
  }

  /**
   * Partial update. The body is a JSON tree rather than a Store because that is the only place the
   * absent-versus-zero distinction survives: bound to a Store, an omitted
   * {@code quantityProductsInStock} and an explicit {@code 0} are both 0, which is why the provided
   * implementation guessed with {@code if (entity.quantityProductsInStock != 0)} and could never set
   * a stock to zero.
   *
   * <p>A field is applied when the key is present. A present {@code name} that is null or blank is
   * 422 — the client asked for a rename and did not supply a name — while an omitted one is simply
   * left alone.
   */
  @PATCH
  @Path("{id}")
  @Transactional
  public Store patch(Long id, ObjectNode updates) {
    if (updates == null || updates.isNull()) {
      throw new WebApplicationException("Store was not set on request.", 400);
    }

    Store entity = Store.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }

    if (updates.has("name")) {
      JsonNode name = updates.get("name");
      if (name.isNull() || !name.isTextual() || name.asText().isBlank()) {
        throw new WebApplicationException("Store Name was not set on request.", 422);
      }
      entity.name = name.asText();
    }

    if (updates.has("quantityProductsInStock")) {
      JsonNode stock = updates.get("quantityProductsInStock");
      if (!stock.isInt()) {
        throw new WebApplicationException(
            "Store quantityProductsInStock must be an integer.", 422);
      }
      entity.quantityProductsInStock = stock.asInt();
    }

    storeChanged.fire(StoreChangedEvent.of(entity, StoreChangedEvent.Operation.UPDATED));

    return entity;
  }

  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Store with id of " + id + " does not exist.", 404);
    }
    entity.delete();
    return Response.status(204).build();
  }

  @Provider
  public static class ErrorMapper implements ExceptionMapper<Exception> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
      LOGGER.error("Failed to handle request", exception);

      int code = 500;
      if (exception instanceof WebApplicationException) {
        code = ((WebApplicationException) exception).getResponse().getStatus();
      }

      ObjectNode exceptionJson = objectMapper.createObjectNode();
      exceptionJson.put("exceptionType", exception.getClass().getName());
      exceptionJson.put("code", code);

      if (exception.getMessage() != null) {
        exceptionJson.put("error", exception.getMessage());
      }

      return Response.status(code).entity(exceptionJson).build();
    }
  }
}
