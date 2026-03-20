# Test Script for Header-Based Kafka Key API
# Tests POST /api/events/publish with X-Kafka-Key header

Write-Host "=== Testing Header-Based Kafka Key API ===" -ForegroundColor Green
Write-Host ""

$baseUrl = "http://localhost:8080"

# Test 1: With Custom Key (Matching Avro Schema)
Write-Host "Test 1: Publishing with custom key (orderId + customerId)" -ForegroundColor Cyan
Write-Host "-------------------------------------------------------" -ForegroundColor Cyan

$headers1 = @{
    "Content-Type" = "application/json"
    "X-Kafka-Key" = '{"orderId":"ORD-HEADER-001","customerId":"CUST-HEADER-001"}'
}

$body1 = @{
    active = $true
    amount = 1234.56
    tradeDate = "2026-03-20"
    createdAt = "2026-03-20T14:45:00.000Z"
    executionTime = 34215123456
    tags = @("header-test", "production-ready", "verified")
    metadata = @{
        source = "powershell-script"
        version = "2.0"
        region = "APAC"
        testType = "header-based-key"
    }
    nestedRecord = @{
        someField = "header-key-test-123"
        count = 99
    }
    status = "CONFIRMED"
    notes = "Testing new header-based key API with full Avro schema"
} | ConvertTo-Json -Depth 10

Write-Host "Request:" -ForegroundColor Yellow
Write-Host "POST $baseUrl/api/events/publish"
Write-Host "X-Kafka-Key: $($headers1['X-Kafka-Key'])"
Write-Host ""

try {
    $response1 = Invoke-RestMethod -Uri "$baseUrl/api/events/publish" `
        -Method POST `
        -Headers $headers1 `
        -Body $body1 `
        -ErrorAction Stop
    
    Write-Host "✅ SUCCESS!" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $response1 | ConvertTo-Json -Depth 5
    Write-Host ""
} catch {
    Write-Host "❌ FAILED!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
}

Start-Sleep -Seconds 2

# Test 2: With Only orderId (customerId null)
Write-Host "Test 2: Publishing with only orderId (customerId null)" -ForegroundColor Cyan
Write-Host "-------------------------------------------------------" -ForegroundColor Cyan

$headers2 = @{
    "Content-Type" = "application/json"
    "X-Kafka-Key" = '{"orderId":"ORD-HEADER-002","customerId":null}'
}

$body2 = @{
    active = $false
    amount = 999.99
    tradeDate = "2026-03-21"
    createdAt = "2026-03-21T10:00:00.000Z"
    executionTime = 36000000000
    tags = @("null-customer-test")
    metadata = @{
        source = "powershell-script"
        version = "1.0"
    }
    nestedRecord = @{
        someField = "test-null-customer"
        count = 1
    }
    status = "PENDING"
    notes = "Testing with null customerId"
} | ConvertTo-Json -Depth 10

Write-Host "Request:" -ForegroundColor Yellow
Write-Host "POST $baseUrl/api/events/publish"
Write-Host "X-Kafka-Key: $($headers2['X-Kafka-Key'])"
Write-Host ""

try {
    $response2 = Invoke-RestMethod -Uri "$baseUrl/api/events/publish" `
        -Method POST `
        -Headers $headers2 `
        -Body $body2 `
        -ErrorAction Stop
    
    Write-Host "✅ SUCCESS!" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $response2 | ConvertTo-Json -Depth 5
    Write-Host ""
} catch {
    Write-Host "❌ FAILED!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
}

Start-Sleep -Seconds 2

# Test 3: Without Key (Auto-generated UUID)
Write-Host "Test 3: Publishing without key (auto-generated UUID)" -ForegroundColor Cyan
Write-Host "-------------------------------------------------------" -ForegroundColor Cyan

$body3 = @{
    active = $true
    amount = 555.55
    tradeDate = "2026-03-22"
    createdAt = "2026-03-22T12:00:00.000Z"
    executionTime = 43200000000
    tags = @("auto-uuid-test")
    metadata = @{
        source = "powershell-script"
        testType = "auto-generated-key"
    }
    nestedRecord = @{
        someField = "uuid-key-test"
        count = 42
    }
    status = "SHIPPED"
    notes = "Testing auto-generated UUID key"
} | ConvertTo-Json -Depth 10

Write-Host "Request:" -ForegroundColor Yellow
Write-Host "POST $baseUrl/api/events/publish"
Write-Host "X-Kafka-Key: (not provided - will auto-generate UUID)"
Write-Host ""

try {
    $response3 = Invoke-RestMethod -Uri "$baseUrl/api/events/publish" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body3 `
        -ErrorAction Stop
    
    Write-Host "✅ SUCCESS!" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $response3 | ConvertTo-Json -Depth 5
    Write-Host ""
} catch {
    Write-Host "❌ FAILED!" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
}

Write-Host ""
Write-Host "=== Test Summary ===" -ForegroundColor Green
Write-Host "Check your application logs to verify:" -ForegroundColor Yellow
Write-Host "1. Test 1: Key orderId=ORD-HEADER-001, customerId=CUST-HEADER-001" -ForegroundColor White
Write-Host "2. Test 2: Key orderId=ORD-HEADER-002, customerId=null" -ForegroundColor White
Write-Host "3. Test 3: Key messageId=<auto-generated-uuid>" -ForegroundColor White
Write-Host ""
Write-Host "All messages should be consumed and logged by the Kafka consumer." -ForegroundColor Yellow
