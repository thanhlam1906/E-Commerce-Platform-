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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE inventory SET quantity = quantity + :q, version = version + 1, updated_at = now() WHERE sku = :sku", nativeQuery = true)
    int increaseQuantity(@Param("sku") String sku, @Param("q") int quantity);
}
