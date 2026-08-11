package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;

/** Delegates to the real reference data — the location table is fixed, so faking it adds nothing. */
public class StaticLocationResolver implements LocationResolver {

  private final LocationGateway delegate = new LocationGateway();

  @Override
  public Location resolveByIdentifier(String identifier) {
    return delegate.resolveByIdentifier(identifier);
  }
}
