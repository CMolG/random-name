package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class WarehouseEndpointIT {

  @Test
  public void testSimpleListWarehouses() {

    final String path = "warehouse";

    // List all, should have all 3 products the database has initially:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(containsString("MWH.001"), containsString("MWH.012"), containsString("MWH.023"));
  }

  /**
   * Creates and archives its own warehouse rather than archiving seed row 1, and asserts on the
   * business unit code rather than the location: locations are shared between warehouses, so
   * asserting a location has disappeared passes or fails for reasons unrelated to archiving.
   */
  @Test
  public void testSimpleCheckingArchivingWarehouses() {

    final String path = "warehouse";

    String id =
        given()
            .contentType("application/json")
            .body(
                "{\"businessUnitCode\":\"MWH.IT1\",\"location\":\"EINDHOVEN-001\",\"capacity\":20,\"stock\":2}")
            .when()
            .post(path)
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .extract()
            .path("id");

    // It is listed while active:
    given().when().get(path).then().statusCode(200).body(containsString("MWH.IT1"));

    // Archive it:
    given().when().delete(path + "/" + id).then().statusCode(204);

    // It is gone from the list, and the seeded warehouses are untouched:
    given()
        .when()
        .get(path)
        .then()
        .statusCode(200)
        .body(
            not(containsString("MWH.IT1")),
            containsString("MWH.001"),
            containsString("MWH.012"),
            containsString("MWH.023"));

    // And it can no longer be fetched:
    given().when().get(path + "/" + id).then().statusCode(404);
  }
}
