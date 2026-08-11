package com.fulfilment.application.monolith.stores;

import static com.fulfilment.application.monolith.stores.RecordingLegacyStoreManagerGateway.CALLS;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.TestFixtures;
import com.fulfilment.application.monolith.fulfilment.FulfilmentAssociationRepository;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The store endpoints and their propagation to the legacy system.
 *
 * <p>Each test that mutates state creates a store of its own, prefixed {@code SYNC} so that
 * {@link TestFixtures} can remove it. The seeded rows are never modified: the whole suite shares one
 * database, so renaming KALLAX here would change what every later test sees.
 */
@QuarkusTest
public class StoreLegacySyncTest {

  @Inject FulfilmentAssociationRepository associations;
  @Inject WarehouseRepository warehouses;
  @Inject ProductRepository products;

  @BeforeEach
  void resetRecordedCallsAndRemoveEarlierFixtures() {
    TestFixtures.clean(associations, warehouses, products);
    RecordingLegacyStoreManagerGateway.reset();
  }

  /** Creates a store and returns its id, so a test never has to mutate a seeded row. */
  private static String givenStore(String name, int stock) {
    String id =
        given()
            .contentType("application/json")
            .body("{\"name\":\"" + name + "\",\"quantityProductsInStock\":" + stock + "}")
            .when()
            .post("store")
            .then()
            .statusCode(201)
            .extract()
            .path("id")
            .toString();
    RecordingLegacyStoreManagerGateway.reset();
    return id;
  }

  @Test
  void aCommittedCreateNotifiesTheLegacySystemOnceWithAnId() {
    given()
        .contentType("application/json")
        .body("{\"name\":\"SYNC-OK\",\"quantityProductsInStock\":3}")
        .when()
        .post("store")
        .then()
        .statusCode(201);

    assertEquals(1, CALLS.size());
    assertEquals("CREATE", CALLS.get(0).operation());
    assertNotNull(CALLS.get(0).id(), "the legacy system must receive a persisted id");
    assertEquals("SYNC-OK", CALLS.get(0).name());
    assertEquals(3, CALLS.get(0).stock());
  }

  @Test
  void aRolledBackTransactionNotifiesNothing() {
    // Store.name is @Column(unique = true), so re-using an existing name makes the commit fail.
    // The gateway call must never happen, which is the entire point of this task.
    given()
        .contentType("application/json")
        .body("{\"name\":\"TONSTAD\",\"quantityProductsInStock\":1}")
        .when()
        .post("store")
        .then()
        .statusCode(greaterThanOrEqualTo(400));

    assertTrue(CALLS.isEmpty(), "a rolled-back transaction must notify nothing");
  }

  @Test
  void aCommittedUpdateNotifiesTheLegacySystemWithTheStoredValues() {
    String id =
        given()
            .contentType("application/json")
            .body("{\"name\":\"SYNC-PUT\",\"quantityProductsInStock\":1}")
            .when()
            .post("store")
            .then()
            .statusCode(201)
            .extract()
            .path("id")
            .toString();

    RecordingLegacyStoreManagerGateway.reset();

    given()
        .contentType("application/json")
        .body("{\"name\":\"SYNC-PUT-2\",\"quantityProductsInStock\":9}")
        .when()
        .put("store/" + id)
        .then()
        .statusCode(200);

    assertEquals(1, CALLS.size());
    assertEquals("UPDATE", CALLS.get(0).operation());
    assertEquals(Long.valueOf(id), CALLS.get(0).id(), "the legacy system must receive the id");
    assertEquals("SYNC-PUT-2", CALLS.get(0).name());
    assertEquals(9, CALLS.get(0).stock());
  }

  @Test
  void patchingOnlyTheNameLeavesStockUnchanged() {
    String id = givenStore("SYNC-PATCH-NAME", 5);

    given()
        .contentType("application/json")
        .body("{\"name\":\"SYNC-PATCH-RENAMED\"}")
        .when()
        .patch("store/" + id)
        .then()
        .statusCode(200)
        .body("quantityProductsInStock", equalTo(5));
  }

  @Test
  void patchingStockToZeroActuallySetsZero() {
    // The discriminating case: every "skip if zero" implementation silently fails here.
    String id = givenStore("SYNC-PATCH-ZERO", 7);

    given()
        .contentType("application/json")
        .body("{\"quantityProductsInStock\":0}")
        .when()
        .patch("store/" + id)
        .then()
        .statusCode(200)
        .body("quantityProductsInStock", equalTo(0));
  }

  @Test
  void patchingWithAPresentButBlankNameIsRejected() {
    String id = givenStore("SYNC-PATCH-BLANK", 1);

    given()
        .contentType("application/json")
        .body("{\"name\":\"\"}")
        .when()
        .patch("store/" + id)
        .then()
        .statusCode(422);
  }

  @Test
  void patchingWithAnAbsentBodyIsRejected() {
    String id = givenStore("SYNC-PATCH-NULL", 1);

    given()
        .contentType("application/json")
        .body("null")
        .when()
        .patch("store/" + id)
        .then()
        .statusCode(400);
  }

  @Test
  void patchingAnUnknownStoreIsNotFound() {
    given()
        .contentType("application/json")
        .body("{\"name\":\"GHOST\"}")
        .when()
        .patch("store/999999")
        .then()
        .statusCode(404);
  }
}
