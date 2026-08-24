-- COD timeout scheduler sweeps WHERE status='PENDING' AND payment_gateway='COD' AND created_at < cutoff.
-- Composite index so the 60s sweep is a range scan instead of a full table scan.
CREATE INDEX idx_orders_cod_pending_exp ON orders (status, payment_gateway, created_at);
