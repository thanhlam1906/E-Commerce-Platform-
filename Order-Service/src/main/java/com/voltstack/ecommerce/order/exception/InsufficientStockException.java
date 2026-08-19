package com.voltstack.ecommerce.order.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class InsufficientStockException extends RuntimeException {
    private final List<String> outOfStockSkus;

    public InsufficientStockException(List<String> outOfStockSkus) {
        super("Sản phẩm không đủ tồn kho: " + String.join(", ", outOfStockSkus));
        this.outOfStockSkus = outOfStockSkus;
    }
}
