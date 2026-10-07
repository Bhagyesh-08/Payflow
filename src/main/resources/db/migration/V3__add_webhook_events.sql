-- Add merchant_id to payments table for webhook lookup
ALTER TABLE payments ADD COLUMN merchant_id BIGINT;
ALTER TABLE payments ADD CONSTRAINT fk_payments_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id);
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);

-- Create webhook_events table for idempotent webhook processing
CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(200) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    gateway_name VARCHAR(50) NOT NULL,
    gateway_payment_id VARCHAR(200),
    payment_id BIGINT,
    payload TEXT NOT NULL,
    signature VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_webhook_events_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- Unique constraint prevents duplicate event processing
CREATE UNIQUE INDEX idx_webhook_events_event_id ON webhook_events(event_id);
CREATE INDEX idx_webhook_events_gateway_payment_id ON webhook_events(gateway_payment_id);
CREATE INDEX idx_webhook_events_payment_id ON webhook_events(payment_id);
CREATE INDEX idx_webhook_events_status ON webhook_events(status);
CREATE INDEX idx_webhook_events_received_at ON webhook_events(received_at);
CREATE INDEX idx_webhook_events_event_type ON webhook_events(event_type);

