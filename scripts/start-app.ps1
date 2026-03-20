# Start Kafka Avro Application with all required environment variables
# This script ensures clean build and proper startup

Write-Host "=== Starting Kafka Avro Application ===" -ForegroundColor Green

# Set environment variables
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:29092"
$env:KAFKA_SCHEMA_REGISTRY_URL="http://localhost:8081"
$env:KAFKA_USERNAME="test"
$env:KAFKA_PASSWORD="test"
$env:KAFKA_SCHEMA_REGISTRY_USERNAME="test"
$env:KAFKA_SCHEMA_REGISTRY_PASSWORD="test"
$env:KAFKA_TOPIC="test-topic"
$env:KAFKA_CONSUMER_GROUP="test-group"

Write-Host "Environment variables set" -ForegroundColor Cyan

# Clean and compile to ensure latest dependencies
Write-Host "Cleaning and compiling..." -ForegroundColor Cyan
mvn clean compile

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed! Please check errors above." -ForegroundColor Red
    exit 1
}

Write-Host "Build successful! Starting application..." -ForegroundColor Green
Write-Host "Swagger UI will be available at: http://localhost:8080/swagger-ui/index.html#/" -ForegroundColor Yellow
Write-Host ""

# Start the application
mvn spring-boot:run
