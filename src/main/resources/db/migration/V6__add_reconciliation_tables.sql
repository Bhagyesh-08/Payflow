-- Reconciliation Runs
CREATE TABLE reconciliation_runs (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    gateway VARCHAR(50) NOT NULL,
    business_date DATE NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    total_records INTEGER,
    matched_records INTEGER,
    discrepancy_records INTEGER,
    unresolved_records INTEGER,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_recon_run UNIQUE (merchant_id, gateway, business_date)
);

CREATE INDEX idx_recon_runs_merchant ON reconciliation_runs(merchant_id);
CREATE INDEX idx_recon_runs_date ON reconciliation_runs(business_date);
CREATE INDEX idx_recon_runs_status ON reconciliation_runs(status);

-- Reconciliation Records (immutable)
CREATE TABLE reconciliation_records (
    id BIGSERIAL PRIMARY KEY,
    reconciliation_run_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    gateway VARCHAR(50) NOT NULL,
    gateway_reference VARCHAR(200) NOT NULL,
    payment_id VARCHAR(100),
    internal_amount DECIMAL(19,2),
    gateway_amount DECIMAL(19,2),
    currency VARCHAR(3) NOT NULL,
    internal_status VARCHAR(30),
    gateway_status VARCHAR(30),
    result VARCHAR(20) NOT NULL,
    discrepancy_type VARCHAR(50) NOT NULL,
    reconciled_at TIMESTAMP,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recon_record_run FOREIGN KEY (reconciliation_run_id) REFERENCES reconciliation_runs(id),
    CONSTRAINT uk_recon_gateway_ref UNIQUE (reconciliation_run_id, gateway, gateway_reference)
);

CREATE INDEX idx_recon_records_run ON reconciliation_records(reconciliation_run_id);
CREATE INDEX idx_recon_records_merchant ON reconciliation_records(merchant_id);
CREATE INDEX idx_recon_records_payment ON reconciliation_records(payment_id);
CREATE INDEX idx_recon_records_result ON reconciliation_records(result);
CREATE INDEX idx_recon_records_discrepancy ON reconciliation_records(discrepancy_type);
