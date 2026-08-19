package com.voltstack.ecommerce.order.exception;

public class SkuNotFoundException extends RuntimeException {
    public SkuNotFoundException(String message) {
        super(message);
    }
}
