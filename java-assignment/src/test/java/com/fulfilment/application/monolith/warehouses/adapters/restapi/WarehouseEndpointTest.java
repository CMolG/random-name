package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The warehouse API against a real database.
 *
 * <p>Every test that mutates state creates the warehouse it then acts on, rather than reaching for
 * a seeded row: the seeds are shared with the other tests in this module and with the integration
 * test, and asserting on them couples unrelated tests together.
 */
@QuarkusTest
public class WarehouseEndpointTest {

  private static final String PATH = "warehouse";

  private static String createWarehouse(String buCode, String location, int capacity, int stock) {
    return given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\":\""
                + buCode
                + "\",\"location\":\""
                + location
                + "\",\"capacity\":"
                + capacity
                + ",\"stock\":"
                + stock
                + "}")
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("businessUnitCode", equalTo(buCode))
        .extract()
        .path("id");
  }

  @Test
  void listingReturnsTheSeededWarehouses() {
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body(containsString("MWH.001"), containsString("MWH.012"), containsString("MWH.023"));
  }

  @Test
  void creatingReturnsTwoHundredAndOneWithAnId() {
    createWarehouse("MWH.201", "AMSTERDAM-002", 10, 1);
  }

  @Test
  void aDuplicateBusinessUnitCodeIsRejectedWithFourHundredAndNine() {
    given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\":\"MWH.001\",\"location\":\"AMSTERDAM-002\",\"capacity\":5,\"stock\":1}")
        .when()
        .post(PATH)
        .then()
        .statusCode(409);
  }

  @Test
  void anUnknownLocationIsRejectedWithFourHundred() {
    given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\":\"MWH.404\",\"location\":\"ATLANTIS-001\",\"capacity\":5,\"stock\":1}")
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void aClientSuppliedIdIsRejectedWithFourHundredAndTwentyTwo() {
    given()
        .contentType("application/json")
        .body(
            "{\"id\":\"99\",\"businessUnitCode\":\"MWH.422\",\"location\":\"AMSTERDAM-002\",\"capacity\":5,\"stock\":1}")
        .when()
        .post(PATH)
        .then()
        .statusCode(422);
  }

  @Test
  void aNonNumericClientSuppliedIdIsAlsoFourHundredAndTwentyTwo() {
    given()
        .contentType("application/json")
        .body(
            "{\"id\":\"abc\",\"businessUnitCode\":\"MWH.423\",\"location\":\"AMSTERDAM-002\",\"capacity\":5,\"stock\":1}")
        .when()
        .post(PATH)
        .then()
        .statusCode(422);
  }

  /**
   * The gate that proves the two validator fixes did not cancel out: adding hibernate-validator
   * without removing the redeclared @NotNull breaks startup, and removing the redeclared @NotNull
   * without the engine present leaves the body unvalidated. Only both together give a 400 here.
   */
  @Test
  void postingANullBodyIsRejectedWithFourHundred() {
    given()
        .contentType("application/json")
        .body("null")
        .when()
        .post(PATH)
        .then()
        .statusCode(400);
  }

  @Test
  void anUnknownIdIsFourHundredAndFour() {
    given().when().get(PATH + "/999999").then().statusCode(404);
  }

  @Test
  void aNonNumericIdIsAlsoFourHundredAndFour() {
    given().when().get(PATH + "/not-a-number").then().statusCode(404);
  }

  @Test
  void aCreatedWarehouseCanBeFetchedByItsId() {
    String id = createWarehouse("MWH.202", "AMSTERDAM-002", 10, 2);

    given()
        .when()
        .get(PATH + "/" + id)
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("MWH.202"))
        .body("stock", equalTo(2));
  }

  @Test
  void archivingReturnsTwoHundredAndFourAndHidesTheWarehouse() {
    String id = createWarehouse("MWH.204", "AMSTERDAM-002", 10, 1);

    given().when().delete(PATH + "/" + id).then().statusCode(204);

    given().when().get(PATH + "/" + id).then().statusCode(404);
    given().when().get(PATH).then().statusCode(200).body(not(containsString("MWH.204")));
  }

  @Test
  void archivingTwiceIsFourHundredAndFour() {
    String id = createWarehouse("MWH.205", "AMSTERDAM-001", 10, 1);

    given().when().delete(PATH + "/" + id).then().statusCode(204);
    given().when().delete(PATH + "/" + id).then().statusCode(404);
  }

  @Test
  void replacingReturnsTwoHundredAndKeepsTheBusinessUnitCode() {
    createWarehouse("MWH.206", "AMSTERDAM-001", 10, 3);

    given()
        .contentType("application/json")
        .body("{\"location\":\"AMSTERDAM-001\",\"capacity\":20,\"stock\":3}")
        .when()
        .post(PATH + "/MWH.206/replacement")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("MWH.206"))
        .body("capacity", equalTo(20));

    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body(containsString("MWH.206"));
  }

  @Test
  void replacingAnUnknownBusinessUnitCodeIsFourHundredAndFour() {
    given()
        .contentType("application/json")
        .body("{\"location\":\"AMSTERDAM-002\",\"capacity\":10,\"stock\":0}")
        .when()
        .post(PATH + "/MWH.999/replacement")
        .then()
        .statusCode(404);
  }

  /**
   * The rejected successor must leave the predecessor active. The creation rules run after the
   * archival, so only the transaction's rollback keeps MWH.207 alive — this is the test the
   * in-memory unit tests cannot write.
   */
  @Test
  void aReplacementRejectedByACreationRuleRollsTheArchivalBack() {
    createWarehouse("MWH.207", "ZWOLLE-002", 20, 4);

    given()
        .contentType("application/json")
        .body("{\"location\":\"ATLANTIS-001\",\"capacity\":20,\"stock\":4}")
        .when()
        .post(PATH + "/MWH.207/replacement")
        .then()
        .statusCode(400);

    given().when().get(PATH).then().statusCode(200).body(containsString("MWH.207"));
  }

  @Test
  void aBodyBusinessUnitCodeThatContradictsThePathIsRejected() {
    createWarehouse("MWH.208", "HELMOND-001", 10, 1);

    given()
        .contentType("application/json")
        .body(
            "{\"businessUnitCode\":\"MWH.OTHER\",\"location\":\"HELMOND-001\",\"capacity\":10,\"stock\":1}")
        .when()
        .post(PATH + "/MWH.208/replacement")
        .then()
        .statusCode(400);
  }
}
