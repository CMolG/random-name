package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import org.junit.jupiter.api.Test;

public class LocationGatewayTest {

  private final LocationGateway locationGateway = new LocationGateway();

  @Test
  public void testWhenResolveExistingLocationShouldReturn() {
    Location location = locationGateway.resolveByIdentifier("ZWOLLE-001");

    assertEquals("ZWOLLE-001", location.identification);
    assertEquals(1, location.maxNumberOfWarehouses);
    assertEquals(40, location.maxCapacity);
  }

  @Test
  public void testWhenResolveUnknownLocationShouldReturnNull() {
    assertNull(locationGateway.resolveByIdentifier("ATLANTIS-001"));
  }

  @Test
  public void testWhenResolveNullOrBlankShouldReturnNull() {
    assertNull(locationGateway.resolveByIdentifier(null));
    assertNull(locationGateway.resolveByIdentifier("   "));
  }

  @Test
  public void testResolveIsCaseSensitiveAndTrimmed() {
    assertEquals("ZWOLLE-001", locationGateway.resolveByIdentifier("  ZWOLLE-001  ").identification);
    assertNull(locationGateway.resolveByIdentifier("zwolle-001"));
  }
}
