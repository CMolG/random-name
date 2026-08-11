package com.fulfilment.application.monolith.fulfilment;

/**
 * The API view of an association.
 *
 * <p>Deliberately not the entity: serialising FulfilmentAssociation would drag the whole Store,
 * Product and DbWarehouse graph into the response and leak the persistence model to clients.
 */
public record FulfilmentResponse(
    Long id,
    Long storeId,
    String storeName,
    Long productId,
    String productName,
    String warehouseBusinessUnitCode) {

  public static FulfilmentResponse of(FulfilmentAssociation association) {
    return new FulfilmentResponse(
        association.id,
        association.store.id,
        association.store.name,
        association.product.id,
        association.product.name,
        association.warehouse.businessUnitCode);
  }
}
