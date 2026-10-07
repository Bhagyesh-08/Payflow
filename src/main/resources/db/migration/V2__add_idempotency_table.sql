-- Create idempotency_records table for database-level duplicate protection
CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    payment_id BIGINT,
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_idempotency_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT uq_merchant_idempotency_key UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_merchant_key ON idempotency_records(merchant_id, idempotency_key);
CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at);

-- Add merchant_id to payments table for idempotency scoping
-- This allows us to scope idempotency keys per merchant
ALTER TABLE payments ADD COLUMN merchant_id BIGINT;

-- Backfill merchant_id from orders (for existing data if any)
UPDATE payments p
SET merchant_id = o.merchant_id
FROM orders o
WHERE p.order_id = o.id;

-- Make merchant_id NOT NULL and add foreign key
ALTER TABLE payments ALTER COLUMN merchant_id SET NOT NULL;
ALTER TABLE payments ADD CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id);

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
