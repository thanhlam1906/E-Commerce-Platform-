package com.voltstack.ecommerce.notification.exception;

/** A failure that retrying can never fix (e.g. template render error). Route to DLQ immediately. */
public class PermanentFailureException extends RuntimeException {

    public PermanentFailureException(String message) {
        super(message);
    }

    public PermanentFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
