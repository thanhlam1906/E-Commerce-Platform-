CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'EXPIRED')),
    total_amount DECIMAL(12,2) NOT NULL CHECK (total_amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    shipping_address_snapshot TEXT NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_gateway VARCHAR(10) NOT NULL,
    payment_transaction_id UUID,
    payment_url VARCHAR(500),
    idempotency_key VARCHAR(100),
    idempotency_request_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    sku VARCHAR(50) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    subtotal DECIMAL(12,2) NOT NULL CHECK (subtotal >= 0),
    CONSTRAINT uq_order_items_sku UNIQUE (order_id, sku)
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by UUID,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
    sku VARCHAR(50) PRIMARY KEY,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reserved INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) NOT NULL REFERENCES inventory(sku),
    type VARCHAR(20) NOT NULL CHECK (type IN ('IMPORT', 'RESERVE', 'DEDUCT', 'RELEASE')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consumed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_status ON orders (user_id, status);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_history_order ON order_status_history (order_id);
CREATE INDEX idx_inv_txn_sku_created ON inventory_transactions (sku, created_at);
CREATE INDEX idx_inv_txn_reference ON inventory_transactions (reference);
CREATE INDEX idx_outbox_published ON outbox (published, created_at);
CREATE INDEX idx_orders_pending_exp ON orders (created_at) WHERE status = 'PENDING';
