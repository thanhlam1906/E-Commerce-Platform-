-- Payment & Billing Service (SRS 06 §5)
-- TIMESTAMPTZ chosen over SRS's TIMESTAMP to match Order-Service and avoid JVM timezone drift.
CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL,                  -- FK logic → Order & Inventory Service
    user_id         UUID NOT NULL,                  -- FK logic → Identity.users
    amount          DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL DEFAULT 'VND', -- VND / USD
    payment_method  VARCHAR(50) NOT NULL,
    gateway         VARCHAR(10) NOT NULL CHECK (gateway IN ('VNPAY','MOMO','STRIPE')),
    gateway_txn_id  VARCHAR(255) UNIQUE,            -- UNIQUE → dedup webhook / chống trùng lệnh
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED','EXPIRED')),
    payment_url     VARCHAR(500),
    refund_amount   DECIMAL(12,2),
    raw_webhook     JSONB,                          -- payload webhook gốc (audit + reconcile)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_order_id ON transactions (order_id);
CREATE INDEX idx_transactions_user_id  ON transactions (user_id);
CREATE INDEX idx_transactions_status_created ON transactions (status, created_at);  -- cho scheduler timeout
CREATE INDEX idx_transactions_gateway_txn ON transactions (gateway_txn_id);         -- phụ trợ dedup

-- Consumer-side dedup (cùng pattern Order & Inventory). Payment-service publishes events
-- and does not consume them today, but the table is part of the SRS data model for future consumers.
CREATE TABLE consumed_events (
    event_id    UUID PRIMARY KEY,
    event_type  VARCHAR(50) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
