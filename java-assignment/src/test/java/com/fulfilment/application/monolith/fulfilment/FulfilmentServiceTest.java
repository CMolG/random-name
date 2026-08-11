package com.fulfilment.application.monolith.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fulfilment.application.monolith.fulfilment.exceptions.DuplicateFulfilmentException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentNotFoundException;
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
 * The three fulfilment limits, each asserted at its boundary: the last allowed association
 * succeeds and the first disallowed one is rejected. A test that only asserts the rejection would
 * still pass if the limit were off by one.
 *
 * <p>Fixtures are persisted directly rather than created through the warehouse use case. The
 * location rules are not what is under test here, and routing fixtures through them would couple
 * these tests to how much capacity the other test classes happen to have consumed.
 */
@QuarkusTest
public class FulfilmentServiceTest {

  @Inject FulfilmentService fulfilmentService;

  // Product and DbWarehouse are plain entities, not PanacheEntity, so they persist via repositories
  @Inject ProductRepository products;
  @Inject WarehouseRepository warehouses;

  private static Long givenStore(String name) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var store = new Store(name);
              store.quantityProductsInStock = 1;
              store.persist();
              return store.id;
            });
  }

  private Long givenProduct(String name) {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              var product = new Product(name);
              product.stock = 1;
              products.persist(product);
              return product.id;
            });
  }

  private String givenWarehouse(String businessUnitCode) {
    return givenWarehouse(businessUnitCode, null);
  }

  private String givenWarehouse(String businessUnitCode, LocalDateTime archivedAt) {
    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              var warehouse = new DbWarehouse();
              warehouse.businessUnitCode = businessUnitCode;
              // VETSBY-001 is reserved for these fixtures: they are persisted directly and would
              // otherwise consume the location capacity the warehouse API tests create against
              warehouse.location = "VETSBY-001";
              warehouse.capacity = 10;
              warehouse.stock = 1;
              warehouse.createdAt = LocalDateTime.now();
              warehouse.archivedAt = archivedAt;
              warehouses.persist(warehouse);
            });
    return businessUnitCode;
  }

  @Test
  void aProductInAStoreMayBeFulfilledByTwoWarehousesButNotThree() {
    Long store = givenStore("F1-STORE");
    Long product = givenProduct("F1-PRODUCT");
    String first = givenWarehouse("F1.W1");
    String second = givenWarehouse("F1.W2");
    String third = givenWarehouse("F1.W3");

    assertNotNull(fulfilmentService.associate(store, product, first));
    assertNotNull(fulfilmentService.associate(store, product, second));

    assertThrows(
        FulfilmentLimitExceededException.class,
        () -> fulfilmentService.associate(store, product, third));
  }

  @Test
  void aStoreMayBeFulfilledByThreeWarehousesButNotFour() {
    Long store = givenStore("F2-STORE");
    Long p1 = givenProduct("F2-P1");
    Long p2 = givenProduct("F2-P2");
    Long p3 = givenProduct("F2-P3");
    Long p4 = givenProduct("F2-P4");

    fulfilmentService.associate(store, p1, givenWarehouse("F2.W1"));
    fulfilmentService.associate(store, p2, givenWarehouse("F2.W2"));
    fulfilmentService.associate(store, p3, givenWarehouse("F2.W3"));

    String fourth = givenWarehouse("F2.W4");
    assertThrows(
        FulfilmentLimitExceededException.class,
        () -> fulfilmentService.associate(store, p4, fourth));
  }

  @Test
  void aWarehouseMayHoldFiveProductsButNotSix() {
    Long store = givenStore("F3-STORE");
    String warehouse = givenWarehouse("F3.W1");

    for (int i = 1; i <= 5; i++) {
      fulfilmentService.associate(store, givenProduct("F3-P" + i), warehouse);
    }

    Long sixth = givenProduct("F3-P6");
    assertThrows(
        FulfilmentLimitExceededException.class,
        () -> fulfilmentService.associate(store, sixth, warehouse));
  }

  @Test
  void theSameStoreProductWarehouseTripleCannotBeAssociatedTwice() {
    Long store = givenStore("F4-STORE");
    Long product = givenProduct("F4-PRODUCT");
    String warehouse = givenWarehouse("F4.W1");

    fulfilmentService.associate(store, product, warehouse);

    assertThrows(
        DuplicateFulfilmentException.class,
        () -> fulfilmentService.associate(store, product, warehouse));
  }

  @Test
  void anArchivedWarehouseCannotFulfilAnything() {
    Long store = givenStore("F5-STORE");
    Long product = givenProduct("F5-PRODUCT");
    String archived = givenWarehouse("F5.W1", LocalDateTime.now());

    assertThrows(
        FulfilmentNotFoundException.class,
        () -> fulfilmentService.associate(store, product, archived));
  }

  @Test
  void anUnknownStoreProductOrWarehouseIsNotFound() {
    Long store = givenStore("F6-STORE");
    Long product = givenProduct("F6-PRODUCT");
    String warehouse = givenWarehouse("F6.W1");

    assertThrows(
        FulfilmentNotFoundException.class,
        () -> fulfilmentService.associate(999999L, product, warehouse));
    assertThrows(
        FulfilmentNotFoundException.class,
        () -> fulfilmentService.associate(store, 999999L, warehouse));
    assertThrows(
        FulfilmentNotFoundException.class,
        () -> fulfilmentService.associate(store, product, "NO.SUCH.WAREHOUSE"));
  }

  /**
   * The qualifier that is easy to get wrong: once a store is at its three-warehouse limit, adding
   * another product to a warehouse it already uses does not change the warehouse count, so it must
   * still be allowed. Counting without the "not already among them" test rejects this.
   */
  @Test
  void addingAProductToAWarehouseTheStoreAlreadyUsesDoesNotCountAgainstTheStoreLimit() {
    Long store = givenStore("F7-STORE");
    Long p1 = givenProduct("F7-P1");
    Long p2 = givenProduct("F7-P2");
    String w1 = givenWarehouse("F7.W1");

    fulfilmentService.associate(store, p1, w1);
    fulfilmentService.associate(store, p1, givenWarehouse("F7.W2"));
    fulfilmentService.associate(store, p2, givenWarehouse("F7.W3"));

    // the store is now at three distinct warehouses; W1 is already one of them
    var association = fulfilmentService.associate(store, p2, w1);

    assertNotNull(association.id);
    assertEquals(4, fulfilmentService.findByStore(store).size());
  }
}
