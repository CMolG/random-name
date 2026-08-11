package com.fulfilment.application.monolith.stores;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records what the legacy system was told, instead of writing temp files.
 *
 * <p>A CDI alternative subclass rather than a Mockito mock: the observer resolves the gateway
 * through injection, so replacing the bean tests the wiring — event, transaction phase and observer
 * — and not just a method call. LegacyStoreManagerGateway itself stays byte-identical to the
 * baseline.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingLegacyStoreManagerGateway extends LegacyStoreManagerGateway {

  public record Call(String operation, Long id, String name, int stock) {}

  public static final List<Call> CALLS = new CopyOnWriteArrayList<>();

  public static void reset() {
    CALLS.clear();
  }

  @Override
  public void createStoreOnLegacySystem(Store store) {
    CALLS.add(new Call("CREATE", store.id, store.name, store.quantityProductsInStock));
  }

  @Override
  public void updateStoreOnLegacySystem(Store store) {
    CALLS.add(new Call("UPDATE", store.id, store.name, store.quantityProductsInStock));
  }
}
