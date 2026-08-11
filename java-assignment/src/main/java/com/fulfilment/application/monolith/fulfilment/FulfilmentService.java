package com.fulfilment.application.monolith.fulfilment;

import com.fulfilment.application.monolith.fulfilment.exceptions.DuplicateFulfilmentException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentLimitExceededException;
import com.fulfilment.application.monolith.fulfilment.exceptions.FulfilmentNotFoundException;
import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The fulfilment rules: which warehouses may fulfil which products for which stores. */
@ApplicationScoped
public class FulfilmentService {

  /** A product in a store may be fulfilled by at most this many warehouses. */
  static final int MAX_WAREHOUSES_PER_PRODUCT_PER_STORE = 2;

  /** A store may be fulfilled by at most this many distinct warehouses. */
  static final int MAX_WAREHOUSES_PER_STORE = 3;

  /** A warehouse may hold at most this many distinct products. */
  static final int MAX_PRODUCTS_PER_WAREHOUSE = 5;

  @Inject FulfilmentAssociationRepository associations;
  @Inject ProductRepository products;

  /** Resolves the warehouse as a business rule — this port never returns archived rows. */
  @Inject WarehouseStore warehouseStore;

  /** Supplies the managed row the association's foreign key needs, once the port has approved it. */
  @Inject WarehouseRepository warehouseRows;

  @Transactional
  public FulfilmentAssociation associate(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {

    Store store = Store.findById(storeId);
    if (store == null) {
      throw new FulfilmentNotFoundException("Store with id of " + storeId + " does not exist.");
    }

    Product product = products.findById(productId);
    if (product == null) {
      throw new FulfilmentNotFoundException("Product with id of " + productId + " does not exist.");
    }

    // an archived warehouse is absent as far as this port is concerned, so it is a 404 for free
    var warehouse = warehouseStore.findByBusinessUnitCode(warehouseBusinessUnitCode);
    if (warehouse == null) {
      throw new FulfilmentNotFoundException(
          "No active warehouse with business unit code " + warehouseBusinessUnitCode + ".");
    }
    DbWarehouse warehouseRow = warehouseRows.findById(warehouse.id);

    if (associations.exists(storeId, productId, warehouse.id)) {
      throw new DuplicateFulfilmentException(
          "Warehouse "
              + warehouseBusinessUnitCode
              + " already fulfils product "
              + productId
              + " for store "
              + storeId
              + ".");
    }

    long warehousesForThisProductInThisStore =
        associations.findByStoreAndProduct(storeId, productId).size();
    if (warehousesForThisProductInThisStore >= MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      throw new FulfilmentLimitExceededException(
          "A product may be fulfilled by at most "
              + MAX_WAREHOUSES_PER_PRODUCT_PER_STORE
              + " warehouses per store.");
    }

    // "and not already among them" matters: adding another product to a warehouse the store
    // already uses does not change how many warehouses fulfil that store
    Set<Long> warehousesForThisStore =
        associations.findByStore(storeId).stream()
            .map(association -> association.warehouse.id)
            .collect(Collectors.toSet());
    if (warehousesForThisStore.size() >= MAX_WAREHOUSES_PER_STORE
        && !warehousesForThisStore.contains(warehouse.id)) {
      throw new FulfilmentLimitExceededException(
          "A store may be fulfilled by at most " + MAX_WAREHOUSES_PER_STORE + " warehouses.");
    }

    Set<Long> productsInThisWarehouse =
        associations.findByWarehouse(warehouse.id).stream()
            .map(association -> association.product.id)
            .collect(Collectors.toSet());
    if (productsInThisWarehouse.size() >= MAX_PRODUCTS_PER_WAREHOUSE
        && !productsInThisWarehouse.contains(productId)) {
      throw new FulfilmentLimitExceededException(
          "A warehouse may hold at most " + MAX_PRODUCTS_PER_WAREHOUSE + " distinct products.");
    }

    var association = new FulfilmentAssociation(store, product, warehouseRow);
    associations.persist(association);
    return association;
  }

  public List<FulfilmentAssociation> findByStore(Long storeId) {
    return associations.findByStore(storeId);
  }

  public List<FulfilmentAssociation> findAll() {
    return associations.listAll();
  }

  @Transactional
  public void remove(Long id) {
    FulfilmentAssociation association = associations.findById(id);
    if (association == null) {
      throw new FulfilmentNotFoundException(
          "Fulfilment association with id of " + id + " does not exist.");
    }
    associations.delete(association);
  }
}
