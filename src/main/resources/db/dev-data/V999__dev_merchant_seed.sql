-- Development Merchant Seed Data
-- This migration is ONLY for local development
-- Flyway will ignore files with version > 998 unless explicitly configured
-- For manual application, run this SQL against your local database

-- NEVER apply this to production
-- NEVER commit actual production credentials

-- Insert test merchant
-- API Key: dev_merchant_key_12345 (for local testing only)
-- This key allows frontend developers to test without manual DB access
INSERT INTO merchants (id, business_name, email, phone, merchant_code, api_key, status, address, created_at, updated_at, version)
VALUES 
    (1, 'Dev Test Merchant', 'dev@payflow.local', '+91-9876543210', 'MER_DEV001', 
     'dev_merchant_key_12345', 'ACTIVE', '123 Test Street, Dev City', 
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence if needed
SELECT setval('merchants_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM merchants), false);

-- Usage Instructions:
-- 1. Start PayFlow backend with PostgreSQL running
-- 2. Use X-API-Key: dev_merchant_key_12345 in all API requests
-- 3. Merchant ID: 1 (automatically derived from API key)
-- 4. Frontend can store this key in local development environment variables
-- 5. For production, merchants register via POST /api/v1/merchants and receive unique keys

