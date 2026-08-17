package com.voltstack.ecommerce.identity.exception;

public class TokenReuseException extends RuntimeException {

    public TokenReuseException(String message) {
        super(message);
    }
}
