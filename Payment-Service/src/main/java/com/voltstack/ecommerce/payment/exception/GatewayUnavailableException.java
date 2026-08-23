package com.voltstack.ecommerce.payment.exception;

/** Gateway createPayment failed (timeout/unreachable) → 502 so Order rolls back the checkout. */
public class GatewayUnavailableException extends RuntimeException {
    public GatewayUnavailableException(String message) {
        super(message);
    }
}
