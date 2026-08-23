-- Outbox pattern (mirrors Order-Service) so payment result events are delivered at-least-once:
-- the status change and the outbox row commit in one transaction, a poller relays to payment.events.
CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_published ON outbox (published, created_at);
