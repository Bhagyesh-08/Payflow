-- PayFlow Local Development Database Seed
-- USE ONLY FOR LOCAL DEVELOPMENT / TESTING ENVIRONMENTS
-- NEVER APPLY TO PRODUCTION

INSERT INTO merchants (
    business_name,
    email,
    phone,
    merchant_code,
    status,
    address,
    api_key,
    created_at,
    updated_at,
    version
) VALUES (
    'Local Dev Store',
    'developer@payflow.local',
    '+91-9876543210',
    'MER_DEV_001',
    'ACTIVE',
    '123 Localhost Blvd, Sandbox City',
    'ak_dev_test_developer_key_local',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
) ON CONFLICT (email) DO NOTHING;
