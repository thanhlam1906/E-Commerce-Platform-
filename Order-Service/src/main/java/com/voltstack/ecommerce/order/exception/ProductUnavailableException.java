package com.voltstack.ecommerce.order.exception;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String message) {
        super(message);
    }
}
