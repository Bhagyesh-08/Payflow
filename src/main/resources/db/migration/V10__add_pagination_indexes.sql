-- V10: Add indexes for pagination performance
-- Phase 9: Production Hardening & API Quality

-- Ledger transactions pagination (merchant_id + createdAt for sorting)
CREATE INDEX IF NOT EXISTS idx_ledger_transactions_merchant_created 
ON ledger_transactions(merchant_id, created_at DESC);

-- Reconciliation runs pagination (merchant_id + createdAt for sorting)
CREATE INDEX IF NOT EXISTS idx_reconciliation_runs_merchant_created 
ON reconciliation_runs(merchant_id, created_at DESC);

-- Reconciliation records pagination for discrepancies
CREATE INDEX IF NOT EXISTS idx_reconciliation_records_merchant_result 
ON reconciliation_records(merchant_id, result, created_at DESC);

-- Comments:
-- These indexes support efficient pagination queries with:
-- 1. Merchant isolation (merchant_id filter)
-- 2. Time-based sorting (created_at DESC)
-- 3. Result filtering for reconciliation records (result column)
--
-- Index selectivity:
-- - merchant_id: High selectivity (many merchants, isolation required)
-- - created_at: Good for time-based range scans
-- - result: Low cardinality (MATCHED/DISCREPANCY) but needed for filtering
