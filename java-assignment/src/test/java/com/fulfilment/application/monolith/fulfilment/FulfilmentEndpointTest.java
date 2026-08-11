package com.fulfilment.application.monolith.fulfilment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

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

/** The fulfilment endpoints, including that the service's failures reach the client as HTTP. */
@QuarkusTest
public class FulfilmentEndpointTest {

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
              warehouses.persist(warehouse);
            });
    return businessUnitCode;
  }

  private static String body(Long storeId, Long productId, String buCode) {
    return "{\"storeId\":"
        + storeId
        + ",\"productId\":"
        + productId
        + ",\"warehouseBusinessUnitCode\":\""
        + buCode
        + "\"}";
  }

  @Test
  void associatingReturnsTwoHundredAndOneAndThenAppearsInTheStoreListing() {
    Long store = givenStore("E1-STORE");
    Long product = givenProduct("E1-PRODUCT");
    String warehouse = givenWarehouse("E1.W1");

    given()
        .contentType("application/json")
        .body(body(store, product, warehouse))
        .when()
        .post("fulfilment")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("warehouseBusinessUnitCode", equalTo(warehouse))
        .body("storeName", equalTo("E1-STORE"));

    given()
        .when()
        .get("fulfilment?storeId=" + store)
        .then()
        .statusCode(200)
        .body("size()", equalTo(1))
        .body("[0].productName", equalTo("E1-PRODUCT"));
  }

  @Test
  void anUnknownWarehouseIsFourHundredAndFour() {
    Long store = givenStore("E2-STORE");
    Long product = givenProduct("E2-PRODUCT");

    given()
        .contentType("application/json")
        .body(body(store, product, "NO.SUCH.WAREHOUSE"))
        .when()
        .post("fulfilment")
        .then()
        .statusCode(404);
  }

  @Test
  void aDuplicateAssociationIsFourHundredAndNine() {
    Long store = givenStore("E3-STORE");
    Long product = givenProduct("E3-PRODUCT");
    String warehouse = givenWarehouse("E3.W1");

    given()
        .contentType("application/json")
        .body(body(store, product, warehouse))
        .when()
        .post("fulfilment")
        .then()
        .statusCode(201);

    given()
        .contentType("application/json")
        .body(body(store, product, warehouse))
        .when()
        .post("fulfilment")
        .then()
        .statusCode(409);
  }

  @Test
  void exceedingALimitIsFourHundred() {
    Long store = givenStore("E4-STORE");
    Long product = givenProduct("E4-PRODUCT");

    for (int i = 1; i <= 2; i++) {
      given()
          .contentType("application/json")
          .body(body(store, product, givenWarehouse("E4.W" + i)))
          .when()
          .post("fulfilment")
          .then()
          .statusCode(201);
    }

    given()
        .contentType("application/json")
        .body(body(store, product, givenWarehouse("E4.W3")))
        .when()
        .post("fulfilment")
        .then()
        .statusCode(400);
  }

  @Test
  void anAssociationCanBeRemoved() {
    Long store = givenStore("E5-STORE");
    Long product = givenProduct("E5-PRODUCT");
    String warehouse = givenWarehouse("E5.W1");

    Integer id =
        given()
            .contentType("application/json")
            .body(body(store, product, warehouse))
            .when()
            .post("fulfilment")
            .then()
            .statusCode(201)
            .extract()
            .path("id");

    given().when().delete("fulfilment/" + id).then().statusCode(204);
    given().when().delete("fulfilment/" + id).then().statusCode(404);

    given()
        .when()
        .get("fulfilment?storeId=" + store)
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
  }

  @Test
  void anIncompleteRequestIsRejected() {
    given()
        .contentType("application/json")
        .body("{\"storeId\":1}")
        .when()
        .post("fulfilment")
        .then()
        .statusCode(400);
  }
}
