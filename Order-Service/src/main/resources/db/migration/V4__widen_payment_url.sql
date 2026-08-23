-- VNPay hosted payment URL with HMAC-SHA512 query signature exceeds VARCHAR(500),
-- mirrored in Payment-Service V3__widen_payment_url.sql. TEXT removes the ceiling.
ALTER TABLE orders ALTER COLUMN payment_url TYPE TEXT;
