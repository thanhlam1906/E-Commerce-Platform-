package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    Optional<Inventory> findBySku(String sku);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE inventory SET reserved = reserved + :q, version = version + 1, updated_at = now() WHERE sku = :sku AND quantity - reserved >= :q", nativeQuery = true)
    int reserve(@Param("sku") String sku, @Param("q") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE inventory SET reserved = GREATEST(reserved - :q, 0), version = version + 1, updated_at = now() WHERE sku = :sku", nativeQuery = true)
    int release(@Param("sku") String sku, @Param("q") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE inventory SET quantity = quantity - :q, reserved = GREATEST(reserved - :q, 0), version = version + 1, updated_at = now() WHERE sku = :sku", nativeQuery = true)
    int deduct(@Param("sku") String sku, @Param("q") int quantity);

    // Single atomic upsert for import. save() on a natural-key entity would go through merge()
    // and fail with StaleObjectState on a missing row, and a two-step UPDATE-then-INSERT would let
    // two concurrent first-time imports of the same SKU both see "no row" and collide on the PK.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO inventory (sku, quantity, reserved, version, updated_at, low_stock_notified) " +
            "VALUES (:sku, :q, 0, 0, now(), false) " +
            "ON CONFLICT (sku) DO UPDATE SET quantity = inventory.quantity + :q, version = inventory.version + 1, updated_at = now()",
            nativeQuery = true)
    int upsertQuantity(@Param("sku") String sku, @Param("q") int quantity);
}
