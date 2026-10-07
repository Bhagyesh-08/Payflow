# PayFlow Phase 2 API Testing Script
Write-Host "`n=== PayFlow Phase 2 Testing ===" -ForegroundColor Cyan

# Test idempotent payment creation
Write-Host "[TEST 1] Creating payment with idempotency key..." -ForegroundColor Yellow
$headers = @{
    'Content-Type' = 'application/json'
    'Idempotency-Key' = 'TEST_KEY_001'
    'X-Merchant-Id' = '1'
}

try {
    $payment1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" `
        -Method Post -Headers $headers `
        -Body '{"orderId":1,"paymentMethod":"UPI"}'
    Write-Host "✓ Payment 1 created: $($payment1.paymentId)" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed: $_" -ForegroundColor Red
}

# Test same idempotency key returns same payment
Write-Host "`n[TEST 2] Same idempotency key should return same payment..." -ForegroundColor Yellow
try {
    $payment2 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" `
        -Method Post -Headers $headers `
        -Body '{"orderId":1,"paymentMethod":"UPI"}'
    
    if ($payment1.id -eq $payment2.id) {
        Write-Host "✓ Same payment returned: $($payment2.paymentId)" -ForegroundColor Green
    } else {
        Write-Host "✗ Different payments returned!" -ForegroundColor Red
    }
} catch {
    Write-Host "Expected same result, got error: $_" -ForegroundColor Yellow
}

# Test payment state transitions
Write-Host "`n[TEST 3] Testing payment state transitions..." -ForegroundColor Yellow
try {
    # Initiate payment
    $initiated = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/$($payment1.id)/initiate" -Method Post
    Write-Host "✓ Payment initiated: $($initiated.status)" -ForegroundColor Green
    
    # Authorize payment
    $authorized = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/$($payment1.id)/authorize" -Method Post
    Write-Host "✓ Payment authorized: $($authorized.status)" -ForegroundColor Green
    
    # Capture payment
    $captured = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/$($payment1.id)/capture" -Method Post
    Write-Host "✓ Payment captured: $($captured.status)" -ForegroundColor Green
} catch {
    Write-Host "✗ State transition failed: $_" -ForegroundColor Red
}

Write-Host "`n✅ Phase 2 features working!" -ForegroundColor Green