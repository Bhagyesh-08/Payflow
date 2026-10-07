-- Enforce Ledger Immutability
-- Prevents UPDATE and DELETE operations on ledger tables to maintain append-only audit trail

-- Create function to prevent updates on ledger_transactions
CREATE OR REPLACE FUNCTION prevent_ledger_transaction_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger transactions are immutable. Use compensating transactions instead.';
END;
$$ LANGUAGE plpgsql;

-- Create function to prevent updates on ledger_entries
CREATE OR REPLACE FUNCTION prevent_ledger_entry_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable. Use compensating transactions instead.';
END;
$$ LANGUAGE plpgsql;

-- Create triggers to prevent UPDATE operations
CREATE TRIGGER prevent_ledger_transaction_update
    BEFORE UPDATE ON ledger_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_transaction_modification();

CREATE TRIGGER prevent_ledger_entry_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_entry_modification();

-- Create triggers to prevent DELETE operations
CREATE TRIGGER prevent_ledger_transaction_delete
    BEFORE DELETE ON ledger_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_transaction_modification();

CREATE TRIGGER prevent_ledger_entry_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION prevent_ledger_entry_modification();
