package com.fulfilment.application.monolith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fulfilment.application.monolith.fulfilment.FulfilmentAssociation;
import com.fulfilment.application.monolith.fulfilment.FulfilmentAssociationRepository;
import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * The cleanup helper the rest of the suite depends on.
 *
 * <p>Worth testing because its failure mode is a foreign key violation in an unrelated test class,
 * which is exactly the kind of error nobody traces back to here.
 */
@QuarkusTest
public class TestFixturesTest {

  @Inject FulfilmentAssociationRepository associations;
  @Inject WarehouseRepository warehouses;
  @Inject ProductRepository products;

  /**
   * An association on a warehouse-endpoint fixture must not survive cleanup.
   *
   * <p>The store and product here are seeded rows, not fixtures, so nothing but the warehouse
   * prefix can match the association. That is the case a filter searching only for fulfilment
   * fixtures misses — and missing it means the warehouse delete that follows fails on the foreign
   * key rather than doing nothing.
   */
  @Test
  void cleanUpRemovesAssociationsHeldByWarehouseEndpointFixtures() {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var warehouse = new DbWarehouse();
              warehouse.businessUnitCode = "MWH.299";
              warehouse.location = "AMSTERDAM-001";
              warehouse.capacity = 10;
              warehouse.stock = 1;
              warehouse.createdAt = LocalDateTime.now();
              warehouses.persist(warehouse);

              Store seededStore = Store.find("name", "KALLAX").firstResult();
              Product seededProduct = products.find("name", "KALLAX").firstResult();
              assertNotNull(seededStore, "expected the seeded KALLAX store");
              assertNotNull(seededProduct, "expected the seeded KALLAX product");

              associations.persist(
                  new FulfilmentAssociation(seededStore, seededProduct, warehouse));
            });

    TestFixtures.clean(associations, warehouses, products);

    assertEquals(
        0,
        warehouses.count("businessUnitCode", "MWH.299"),
        "the fixture warehouse must be gone, not blocked by an association");
    assertEquals(
        0,
        associations.count("warehouse.businessUnitCode = ?1", "MWH.299"),
        "the association must be gone with it");
    assertNotNull(Store.find("name", "KALLAX").firstResult(), "the seeded store must survive");
    assertNotNull(
        products.find("name", "KALLAX").firstResult(), "the seeded product must survive");
  }
}
