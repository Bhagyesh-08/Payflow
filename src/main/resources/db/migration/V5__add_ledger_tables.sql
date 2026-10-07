-- Ledger Accounts
CREATE TABLE ledger_accounts (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT,
    account_code VARCHAR(50) NOT NULL UNIQUE,
    account_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ledger_account UNIQUE (merchant_id, account_type, currency)
);

CREATE INDEX idx_ledger_accounts_merchant ON ledger_accounts(merchant_id);
CREATE INDEX idx_ledger_accounts_type ON ledger_accounts(account_type);

-- Ledger Transactions (immutable)
CREATE TABLE ledger_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_reference VARCHAR(200) NOT NULL UNIQUE,
    payment_id VARCHAR(100),
    merchant_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ledger_transactions_payment ON ledger_transactions(payment_id);
CREATE INDEX idx_ledger_transactions_merchant ON ledger_transactions(merchant_id);
CREATE INDEX idx_ledger_transactions_created ON ledger_transactions(created_at);

-- Ledger Entries (immutable)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ledger_entry_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id)
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_type ON ledger_entries(entry_type);
