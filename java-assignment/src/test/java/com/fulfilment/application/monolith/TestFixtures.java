package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.fulfilment.FulfilmentAssociationRepository;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Removes rows created by test fixtures, leaving the {@code import.sql} seed data untouched.
 *
 * <p>Why this exists: Quarkus starts <b>one</b> application and one database for the entire test
 * run — not one per test class — and nothing rolls back what a RestAssured call committed, because
 * the request is handled by the server in its own transaction. Every {@code @QuarkusTest} in this
 * module therefore shares one database, and fixtures left behind by one class are visible to every
 * class that runs after it. That is not a theoretical concern: warehouse creation enforces per
 * location limits, so leftover warehouses at a location made unrelated tests fail with no visible
 * cause.
 *
 * <p>Called from {@code @BeforeEach}, never {@code @AfterEach}: a test that fails part-way must not
 * be able to poison the next one, and cleaning on the way in is the only ordering that guarantees
 * it.
 *
 * <p>Deletion is by fixture naming prefix rather than by truncation, so the seeded stores, products
 * and warehouses survive. Rows are removed one at a time rather than by a bulk JPQL delete: these
 * entities are {@code @Cacheable}, and going through the persistence context keeps the second-level
 * cache consistent.
 *
 * <p><b>Every fixture a test creates must use one of the prefixes below</b>, or it will not be
 * cleaned up and will leak into other test classes.
 */
public final class TestFixtures {

  /** Warehouses created by the warehouse endpoint tests. The seeds are MWH.001, MWH.012, MWH.023. */
  public static final String WAREHOUSE_ENDPOINT = "MWH.2%";

  /** Stores, products and warehouses created by the fulfilment tests. */
  public static final String FULFILMENT = "FE%";

  /** Stores created by the store legacy-sync tests. */
  public static final String STORE_SYNC = "SYNC%";

  private TestFixtures() {}

  public static void clean(
      FulfilmentAssociationRepository associations,
      WarehouseRepository warehouses,
      ProductRepository products) {

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              // associations first: they hold the foreign keys into everything below
              associations
                  .list(
                      "warehouse.businessUnitCode like ?1 or store.name like ?1"
                          + " or product.name like ?1",
                      FULFILMENT)
                  .forEach(associations::delete);

              warehouses
                  .list("businessUnitCode like ?1 or businessUnitCode like ?2",
                      WAREHOUSE_ENDPOINT, FULFILMENT)
                  .forEach(warehouses::delete);

              products.list("name like ?1", FULFILMENT).forEach(products::delete);

              Store.<Store>list("name like ?1 or name like ?2", FULFILMENT, STORE_SYNC)
                  .forEach(Store::delete);
            });
  }
}
