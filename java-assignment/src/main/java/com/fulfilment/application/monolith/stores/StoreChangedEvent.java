package com.fulfilment.application.monolith.stores;

/**
 * Immutable snapshot of a committed store change.
 *
 * <p>A snapshot rather than the entity: observers run after the transaction ends, when the
 * persistence context is gone, so reading a managed entity there is reading a detached instance.
 * Capturing the values inside the transaction guarantees the legacy system receives what was
 * actually committed.
 */
public record StoreChangedEvent(
    Long id, String name, int quantityProductsInStock, Operation operation) {

  /** No DELETE: the legacy gateway exposes no delete operation, so it would be dead code. */
  public enum Operation {
    CREATED,
    UPDATED
  }

  public static StoreChangedEvent of(Store store, Operation operation) {
    return new StoreChangedEvent(store.id, store.name, store.quantityProductsInStock, operation);
  }
}
