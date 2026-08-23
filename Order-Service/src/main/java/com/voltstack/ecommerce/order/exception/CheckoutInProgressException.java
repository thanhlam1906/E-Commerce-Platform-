package com.voltstack.ecommerce.order.exception;

/** Two checkouts for the same user collided on the Redis lock — a designed race, return 409 not 500. */
public class CheckoutInProgressException extends IllegalStateException {
    public CheckoutInProgressException(String message) {
        super(message);
    }
}
