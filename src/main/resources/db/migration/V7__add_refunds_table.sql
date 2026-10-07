-- Refunds table
CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    refund_id VARCHAR(100) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway VARCHAR(50),
    gateway_refund_id VARCHAR(200),
    idempotency_key VARCHAR(255) NOT NULL,
    reason VARCHAR(1000),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_refund_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT uk_refund_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_refunds_payment ON refunds(payment_id);
CREATE INDEX idx_refunds_merchant ON refunds(merchant_id);
CREATE INDEX idx_refunds_status ON refunds(status);
CREATE INDEX idx_refunds_gateway_ref ON refunds(gateway_refund_id);

-- Add CUSTOMER_REFUND account type support (already in AccountType enum as REFUND_PAYABLE)
-- No schema change needed, AccountType enum handles this
