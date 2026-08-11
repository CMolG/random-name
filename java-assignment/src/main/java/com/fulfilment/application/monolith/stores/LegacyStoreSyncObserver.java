package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Propagates committed store changes to the legacy system.
 *
 * <p>Sitting behind an AFTER_SUCCESS observer is the whole point: the provided code called the
 * gateway inside the transaction, so a later rollback left the legacy system believing in a store
 * the database never kept.
 */
@ApplicationScoped
public class LegacyStoreSyncObserver {

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  /**
   * Fires only after the transaction commits, so a rollback propagates nothing.
   *
   * <p>Rebuilds a transient Store because the provided gateway's signature takes one and stays
   * byte-identical to the baseline.
   */
  void onStoreChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreChangedEvent event) {
    Store store = new Store(event.name());
    store.id = event.id();
    store.quantityProductsInStock = event.quantityProductsInStock();

    switch (event.operation()) {
      case CREATED -> legacyStoreManagerGateway.createStoreOnLegacySystem(store);
      case UPDATED -> legacyStoreManagerGateway.updateStoreOnLegacySystem(store);
    }
  }
}
