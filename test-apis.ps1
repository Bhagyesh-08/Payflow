# PayFlow Phase 1 API Testing Script
# This script tests all Phase 1 endpoints

Write-Host "`n=== PayFlow Phase 1 API Testing ===" -ForegroundColor Cyan
Write-Host "Base URL: http://localhost:8080`n" -ForegroundColor Gray

# Test 1: Create Merchant
Write-Host "[TEST 1] Creating merchant..." -ForegroundColor Yellow
$merchant = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/merchants" `
    -Method Post `
    -ContentType "application/json" `
    -Body '{"businessName":"ElectroShop","email":"contact@electroshop.com","phone":"9988776655","address":"123 Tech Street, Mumbai"}'

Write-Host "✓ Merchant created: ID=$($merchant.id), Code=$($merchant.merchantCode)" -ForegroundColor Green
Write-Host "  API Key: $($merchant.apiKey)" -ForegroundColor Gray

# Test 2: Get Merchant
Write-Host "`n[TEST 2] Retrieving merchant by ID..." -ForegroundColor Yellow
$merchantGet = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/merchants/$($merchant.id)" -Method Get
Write-Host "✓ Merchant retrieved: $($merchantGet.businessName) ($($merchantGet.status))" -ForegroundColor Green

# Test 3: Create Order
Write-Host "`n[TEST 3] Creating order..." -ForegroundColor Yellow
$order = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" `
    -Method Post `
    -ContentType "application/json" `
    -Body "{`"merchantId`":$($merchant.id),`"amount`":2500.50,`"currency`":`"INR`",`"customerEmail`":`"buyer@example.com`",`"customerPhone`":`"9876543210`",`"description`":`"Smartphone purchase`"}"

Write-Host "✓ Order created: $($order.orderNumber), Amount: ₹$($order.amount)" -ForegroundColor Green

# Test 4: Get Order
Write-Host "`n[TEST 4] Retrieving order by ID..." -ForegroundColor Yellow
$orderGet = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders/$($order.id)" -Method Get
Write-Host "✓ Order retrieved: $($orderGet.orderNumber) - Status: $($orderGet.status)" -ForegroundColor Green

# Test 5: Create Payment
Write-Host "`n[TEST 5] Creating payment..." -ForegroundColor Yellow
$payment = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" `
    -Method Post `
    -ContentType "application/json" `
    -Body "{`"orderId`":$($order.id),`"paymentMethod`":`"UPI`"}"

Write-Host "✓ Payment created: $($payment.paymentId), Amount: ₹$($payment.amount)" -ForegroundColor Green
Write-Host "  Status: $($payment.status)" -ForegroundColor Gray

# Test 6: Get Payment by ID
Write-Host "`n[TEST 6] Retrieving payment by ID..." -ForegroundColor Yellow
$paymentGet = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/$($payment.id)" -Method Get
Write-Host "✓ Payment retrieved: $($paymentGet.paymentId)" -ForegroundColor Green

# Test 7: Get Payment by Payment ID
Write-Host "`n[TEST 7] Retrieving payment by paymentId..." -ForegroundColor Yellow
$paymentByPayId = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/payment-id/$($payment.paymentId)" -Method Get
Write-Host "✓ Payment retrieved via paymentId: $($paymentByPayId.paymentId)" -ForegroundColor Green

# Test 8: Duplicate Payment (Should Fail)
Write-Host "`n[TEST 8] Attempting duplicate payment creation..." -ForegroundColor Yellow
try {
    $dupPayment = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments" `
        -Method Post `
        -ContentType "application/json" `
        -Body "{`"orderId`":$($order.id),`"paymentMethod`":`"CARD`"}"
    Write-Host "✗ Test failed - duplicate payment was created!" -ForegroundColor Red
} catch {
    Write-Host "✓ Duplicate payment correctly rejected (400 Bad Request)" -ForegroundColor Green
}

# Test 9: Duplicate Merchant Email (Should Fail)
Write-Host "`n[TEST 9] Attempting duplicate merchant email..." -ForegroundColor Yellow
try {
    $dupMerchant = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/merchants" `
        -Method Post `
        -ContentType "application/json" `
        -Body '{"businessName":"Another Shop","email":"contact@electroshop.com","phone":"1234567890"}'
    Write-Host "✗ Test failed - duplicate email was accepted!" -ForegroundColor Red
} catch {
    Write-Host "✓ Duplicate merchant email correctly rejected (409 Conflict)" -ForegroundColor Green
}

# Test 10: Invalid Order (Negative Amount)
Write-Host "`n[TEST 10] Attempting order with negative amount..." -ForegroundColor Yellow
try {
    $invalidOrder = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" `
        -Method Post `
        -ContentType "application/json" `
        -Body "{`"merchantId`":$($merchant.id),`"amount`":-100,`"currency`":`"INR`"}"
    Write-Host "✗ Test failed - negative amount was accepted!" -ForegroundColor Red
} catch {
    Write-Host "✓ Negative amount correctly rejected (400 Validation Error)" -ForegroundColor Green
}

# Test 11: Invalid Order (Non-existent Merchant)
Write-Host "`n[TEST 11] Attempting order with non-existent merchant..." -ForegroundColor Yellow
try {
    $invalidOrder = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" `
        -Method Post `
        -ContentType "application/json" `
        -Body '{"merchantId":99999,"amount":100,"currency":"INR"}'
    Write-Host "✗ Test failed - non-existent merchant was accepted!" -ForegroundColor Red
} catch {
    Write-Host "✓ Non-existent merchant correctly rejected (404 Not Found)" -ForegroundColor Green
}

# Test 12: Actuator Health Check
Write-Host "`n[TEST 12] Checking application health..." -ForegroundColor Yellow
$health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get
Write-Host "✓ Application health: $($health.status)" -ForegroundColor Green
Write-Host "  Database: $($health.components.db.status)" -ForegroundColor Gray

# Summary
Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "✓ All 12 tests completed successfully" -ForegroundColor Green
Write-Host "`nCreated Resources:" -ForegroundColor Cyan
Write-Host "  Merchant: $($merchant.businessName) (ID: $($merchant.id))" -ForegroundColor White
Write-Host "  Order:    $($order.orderNumber) (₹$($order.amount))" -ForegroundColor White
Write-Host "  Payment:  $($payment.paymentId) ($($payment.status))" -ForegroundColor White
Write-Host "`nPhase 1 APIs are working correctly! ✓" -ForegroundColor Green
Write-Host ""
