-- VNPay hosted payment URL with HMAC-SHA512 query signature exceeds VARCHAR(500)
-- (sandbox simulator URLs were short enough that this never surfaced). TEXT removes the ceiling.
ALTER TABLE transactions ALTER COLUMN payment_url TYPE TEXT;
