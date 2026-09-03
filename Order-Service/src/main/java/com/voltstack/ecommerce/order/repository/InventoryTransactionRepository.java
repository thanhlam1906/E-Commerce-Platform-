package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.InventoryTransaction;
import com.voltstack.ecommerce.order.entity.InventoryTxnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    List<InventoryTransaction> findBySkuOrderByCreatedAtDesc(String sku);

    /** Số lượng đã khấu trừ (DEDUCT) gộp theo SKU — nền tảng cho "số đã bán". */
    @Query("SELECT t.sku, SUM(t.quantity) FROM InventoryTransaction t " +
            "WHERE t.type = :type AND t.sku IN :skus GROUP BY t.sku")
    List<Object[]> sumQuantityBySkuIn(@Param("type") InventoryTxnType type, @Param("skus") List<String> skus);
}
