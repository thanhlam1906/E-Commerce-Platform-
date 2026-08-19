package com.voltstack.ecommerce.notification.exception;

import java.util.UUID;

/** A consumed event is structurally invalid (bad JSON, missing eventId/eventType/recipient). */
public class MalformedEventException extends RuntimeException {

    private final UUID eventId;

    public MalformedEventException(String message, UUID eventId) {
        super(message);
        this.eventId = eventId;
    }

    public UUID getEventId() {
        return eventId;
    }
}
