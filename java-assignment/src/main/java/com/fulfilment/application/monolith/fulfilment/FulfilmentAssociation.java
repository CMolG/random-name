package com.fulfilment.application.monolith.fulfilment;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A warehouse fulfilling a product for a store.
 *
 * <p>The unique constraint on the triple is the database's own copy of the "no duplicate
 * association" rule. The service checks it too, so callers get a 409 rather than a 500, but the
 * constraint is what holds under a race the service's read-then-write cannot see.
 */
@Entity
@Table(
    name = "fulfilment_association",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_fulfilment_store_product_warehouse",
            columnNames = {"store_id", "product_id", "warehouse_id"}))
public class FulfilmentAssociation extends PanacheEntity {

  @ManyToOne(optional = false)
  @JoinColumn(name = "store_id", nullable = false)
  public Store store;

  @ManyToOne(optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  public Product product;

  @ManyToOne(optional = false)
  @JoinColumn(name = "warehouse_id", nullable = false)
  public DbWarehouse warehouse;

  public FulfilmentAssociation() {}

  public FulfilmentAssociation(Store store, Product product, DbWarehouse warehouse) {
    this.store = store;
    this.product = product;
    this.warehouse = warehouse;
  }
}
