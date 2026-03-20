# ============================================================================
# Automated Schema Registration Script
# ============================================================================
# This script registers all Avro schemas (v1 and v2) to Schema Registry
# Usage: .\register-all-schemas.ps1
# ============================================================================

$SCHEMA_REGISTRY_URL = "http://localhost:8081"
$TOPIC_NAME = "test-topic"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Schema Registration Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Function to register a schema
function Register-Schema {
    param(
        [string]$Subject,
        [string]$SchemaFile,
        [string]$Version
    )
    
    Write-Host "Registering $Version schema for subject: $Subject" -ForegroundColor Yellow
    
    try {
        $response = Invoke-RestMethod `
            -Uri "$SCHEMA_REGISTRY_URL/subjects/$Subject/versions" `
            -Method Post `
            -ContentType "application/vnd.schemaregistry.v1+json" `
            -Body (Get-Content $SchemaFile -Raw)
        
        Write-Host "✓ Success! Schema ID: $($response.id)" -ForegroundColor Green
        return $true
    }
    catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Check if Schema Registry is accessible
Write-Host "Checking Schema Registry connectivity..." -ForegroundColor Yellow
try {
    $subjects = Invoke-RestMethod -Uri "$SCHEMA_REGISTRY_URL/subjects" -Method Get
    Write-Host "✓ Schema Registry is accessible" -ForegroundColor Green
    Write-Host ""
}
catch {
    Write-Host "✗ Cannot connect to Schema Registry at $SCHEMA_REGISTRY_URL" -ForegroundColor Red
    Write-Host "Please ensure Schema Registry is running (docker-compose up -d)" -ForegroundColor Red
    exit 1
}

# Register V1 Schemas
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Registering V1 Schemas" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$v1KeySuccess = Register-Schema `
    -Subject "$TOPIC_NAME-key" `
    -SchemaFile "src/main/resources/schemas/v1/key-schema.json" `
    -Version "V1"

Write-Host ""

$v1ValueSuccess = Register-Schema `
    -Subject "$TOPIC_NAME-value" `
    -SchemaFile "src/main/resources/schemas/v1/value-schema.json" `
    -Version "V1"

Write-Host ""

# Register V2 Schemas
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Registering V2 Schemas" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$v2KeySuccess = Register-Schema `
    -Subject "$TOPIC_NAME-key" `
    -SchemaFile "src/main/resources/schemas/v2/key-schema.json" `
    -Version "V2"

Write-Host ""

$v2ValueSuccess = Register-Schema `
    -Subject "$TOPIC_NAME-value" `
    -SchemaFile "src/main/resources/schemas/v2/value-schema.json" `
    -Version "V2"

Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Registration Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$totalSchemas = 4
$successCount = 0
if ($v1KeySuccess) { $successCount++ }
if ($v1ValueSuccess) { $successCount++ }
if ($v2KeySuccess) { $successCount++ }
if ($v2ValueSuccess) { $successCount++ }

Write-Host "Total Schemas: $totalSchemas" -ForegroundColor White
Write-Host "Successful: $successCount" -ForegroundColor Green
Write-Host "Failed: $($totalSchemas - $successCount)" -ForegroundColor Red
Write-Host ""

# Verify registered schemas
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

try {
    $subjects = Invoke-RestMethod -Uri "$SCHEMA_REGISTRY_URL/subjects" -Method Get
    Write-Host "Registered subjects:" -ForegroundColor Yellow
    foreach ($subject in $subjects) {
        $versions = Invoke-RestMethod -Uri "$SCHEMA_REGISTRY_URL/subjects/$subject/versions" -Method Get
        Write-Host "  • $subject (versions: $($versions -join ', '))" -ForegroundColor White
    }
}
catch {
    Write-Host "Could not retrieve subjects" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Registration Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Start the application: mvn spring-boot:run" -ForegroundColor White
Write-Host "2. Access Swagger UI: http://localhost:8080/swagger-ui/index.html#/" -ForegroundColor White
Write-Host "3. Verify schemas via API: GET /api/registry/subjects" -ForegroundColor White
Write-Host ""
