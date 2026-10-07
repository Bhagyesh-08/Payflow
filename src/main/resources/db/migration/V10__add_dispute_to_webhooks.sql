-- V10: Add Dispute Support to Webhooks

ALTER TABLE webhook_events ADD COLUMN gateway_dispute_id VARCHAR(200);
ALTER TABLE webhook_events ADD COLUMN dispute_id BIGINT REFERENCES disputes(id);

CREATE INDEX idx_webhook_events_gateway_dispute_id ON webhook_events(gateway_dispute_id);
CREATE INDEX idx_webhook_events_dispute_id ON webhook_events(dispute_id);
