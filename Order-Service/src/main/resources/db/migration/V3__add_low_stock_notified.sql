-- Dedup InventoryLowEvent via a per-SKU flag instead of "unpublished outbox row",
-- so the event is not re-emitted on every transaction once the first one is published.
ALTER TABLE inventory ADD COLUMN low_stock_notified BOOLEAN NOT NULL DEFAULT FALSE;
