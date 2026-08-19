package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    List<InventoryTransaction> findBySkuOrderByCreatedAtDesc(String sku);
}
