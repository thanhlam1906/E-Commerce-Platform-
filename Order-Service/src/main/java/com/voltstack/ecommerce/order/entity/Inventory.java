package com.voltstack.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    @Builder.Default
    private Integer reserved = 0;

    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** True while a low-stock alert is in effect; reset when stock returns above threshold. */
    @Column(name = "low_stock_notified", nullable = false)
    @Builder.Default
    private Boolean lowStockNotified = false;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
