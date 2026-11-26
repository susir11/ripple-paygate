-- Store money exactly (NUMERIC) instead of as a floating-point DOUBLE.
-- "SET DATA TYPE" is standard SQL, understood by PostgreSQL and by H2 in tests.
ALTER TABLE payments ALTER COLUMN amount SET DATA TYPE NUMERIC(12, 2);

-- Who made the payment (from the gateway's X-Authenticated-User header).
ALTER TABLE payments ADD COLUMN username VARCHAR(255);

-- When the payment was started; used to find stale PENDING payments.
ALTER TABLE payments ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;

-- Each eSewa transaction id belongs to exactly one payment.
CREATE UNIQUE INDEX payments_transaction_id_key ON payments (transaction_id);

-- Fast lookups for "my payments" and for the reconciler.
CREATE INDEX payments_username_idx ON payments (username);
CREATE INDEX payments_status_created_at_idx ON payments (status, created_at);
