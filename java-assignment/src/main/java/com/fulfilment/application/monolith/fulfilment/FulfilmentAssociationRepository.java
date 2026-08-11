package com.fulfilment.application.monolith.fulfilment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class FulfilmentAssociationRepository implements PanacheRepository<FulfilmentAssociation> {

  public List<FulfilmentAssociation> findByStore(Long storeId) {
    return list("store.id", storeId);
  }

  public List<FulfilmentAssociation> findByStoreAndProduct(Long storeId, Long productId) {
    return list("store.id = ?1 and product.id = ?2", storeId, productId);
  }

  public List<FulfilmentAssociation> findByWarehouse(Long warehouseId) {
    return list("warehouse.id", warehouseId);
  }

  public boolean exists(Long storeId, Long productId, Long warehouseId) {
    return count(
            "store.id = ?1 and product.id = ?2 and warehouse.id = ?3",
            storeId,
            productId,
            warehouseId)
        > 0;
  }
}
