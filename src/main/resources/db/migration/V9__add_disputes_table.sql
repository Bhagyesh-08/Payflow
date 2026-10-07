-- V9: Add Disputes Table

CREATE TABLE disputes (
    id BIGSERIAL PRIMARY KEY,
    dispute_id VARCHAR(100) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    merchant_id BIGINT NOT NULL REFERENCES merchants(id),
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    gateway VARCHAR(50),
    gateway_dispute_id VARCHAR(200) UNIQUE,
    reason VARCHAR(1000),
    evidence_due_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_reason VARCHAR(1000),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_disputes_dispute_id ON disputes(dispute_id);
CREATE INDEX idx_disputes_payment_id ON disputes(payment_id);
CREATE INDEX idx_disputes_merchant_id ON disputes(merchant_id);
CREATE INDEX idx_disputes_gateway_dispute_id ON disputes(gateway_dispute_id);
CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_disputes_created_at ON disputes(created_at);
