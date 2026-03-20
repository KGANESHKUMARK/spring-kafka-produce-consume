# Header-Based Kafka Key API Guide

## New Endpoint: POST /api/events/publish

This endpoint provides a cleaner, more production-ready approach to publishing Kafka events by accepting the message key via HTTP header instead of the request body.

---

## Endpoint Details

**URL:** `POST /api/events/publish`

**Headers:**
- `Content-Type: application/json` (required)
- `X-Kafka-Key: <JSON string>` (optional)

**Body:** Pure payload data (no wrapper object)

---

## Key Features

### 1. **Optional Key with Auto-Generation**
- If `X-Kafka-Key` header is provided → Uses it as the Kafka message key
- If `X-Kafka-Key` header is missing → Auto-generates UUID as key: `{"messageId":"<uuid>"}`

### 2. **Clean Request Structure**
- Body contains only the actual payload data
- No nested `contractDataKey` or `payload` wrappers
- Aligns with Kafka's separation of keys and values

### 3. **Backward Compatibility**
- Old endpoint `POST /api/events` still works unchanged
- No breaking changes to existing integrations

---

## Usage Examples

### Example 1: With Custom Key (Recommended)

**Request:**
```bash
POST http://localhost:8080/api/events/publish
Content-Type: application/json
X-Kafka-Key: {"orderId":"ORD-12345","customerId":"CUST-67890"}

{
  "active": true,
  "amount": 1234.56,
  "tradeDate": "2026-03-20",
  "createdAt": "2026-03-20T01:45:00.000Z",
  "executionTime": 34215123456,
  "tags": ["urgent", "high-value", "verified"],
  "metadata": {
    "source": "web-portal",
    "version": "2.0",
    "region": "APAC"
  },
  "nestedRecord": {
    "someField": "nested-value-123",
    "count": 42
  },
  "status": "CONFIRMED",
  "notes": "This is a test with header-based key"
}
```

**PowerShell:**
```powershell
$headers = @{
    "Content-Type" = "application/json"
    "X-Kafka-Key" = '{"orderId":"ORD-12345","customerId":"CUST-67890"}'
}

$body = Get-Content "sample-requests/publish-with-header-key.json" -Raw

Invoke-RestMethod -Uri "http://localhost:8080/api/events/publish" `
    -Method POST `
    -Headers $headers `
    -Body $body
```

---

### Example 2: Without Key (UUID Auto-Generated)

**Request:**
```bash
POST http://localhost:8080/api/events/publish
Content-Type: application/json

{
  "active": true,
  "amount": 9999.99,
  "tradeDate": "2026-03-20",
  "createdAt": "2026-03-20T10:00:00.000Z",
  "executionTime": 36000000000,
  "tags": ["auto-key"],
  "metadata": {
    "source": "api",
    "version": "1.0"
  },
  "nestedRecord": {
    "someField": "test",
    "count": 1
  },
  "status": "PENDING",
  "notes": "Key will be auto-generated"
}
```

**PowerShell:**
```powershell
$body = @{
    active = $true
    amount = 9999.99
    tradeDate = "2026-03-20"
    createdAt = "2026-03-20T10:00:00.000Z"
    executionTime = 36000000000
    tags = @("auto-key")
    metadata = @{
        source = "api"
        version = "1.0"
    }
    nestedRecord = @{
        someField = "test"
        count = 1
    }
    status = "PENDING"
    notes = "Key will be auto-generated"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/events/publish" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

**Generated Key:** `{"messageId":"550e8400-e29b-41d4-a716-446655440000"}` (example UUID)

---

## Response Format

**Success (200 OK):**
```json
{
  "topic": "test-topic",
  "partition": 0,
  "offset": 42,
  "timestamp": 1711234567890,
  "keySchemaId": 3,
  "valueSchemaId": 4
}
```

**Error (400 Bad Request):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "payload must not be null",
  "timestamp": "2026-03-20T06:30:00.123Z"
}
```

**Error (400 Bad Request - Invalid Key JSON):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid JSON in X-Kafka-Key header: Unexpected character...",
  "timestamp": "2026-03-20T06:30:00.123Z"
}
```

---

## Comparison: Old vs New Endpoint

### Old Endpoint: POST /api/events
```json
{
  "contractDataKey": {
    "orderId": "ORD-12345",
    "customerId": "CUST-67890"
  },
  "payload": {
    "active": true,
    "amount": 1234.56,
    ...
  }
}
```

### New Endpoint: POST /api/events/publish
```
Header: X-Kafka-Key: {"orderId":"ORD-12345","customerId":"CUST-67890"}
Body: {
  "active": true,
  "amount": 1234.56,
  ...
}
```

---

## Benefits

✅ **Cleaner API Design** - Separates routing metadata (key) from business data (payload)  
✅ **Kafka-Native Pattern** - Aligns with Kafka's key/value separation  
✅ **Flexible** - Key is optional, auto-generates UUID if missing  
✅ **Production-Ready** - Industry-standard approach  
✅ **No Breaking Changes** - Old endpoint still works  

---

## Migration Path

1. **Phase 1 (Current):** Both endpoints available
   - Old: `POST /api/events` (with nested structure)
   - New: `POST /api/events/publish` (with header key)

2. **Phase 2 (Recommended):** Migrate to new endpoint
   - Update client applications to use `POST /api/events/publish`
   - Test thoroughly with both key patterns (custom + auto-generated)

3. **Phase 3 (Future):** Deprecate old endpoint
   - Mark `POST /api/events` as deprecated
   - Remove after all clients migrated

---

## Testing

### Manual Test (PowerShell)
```powershell
# Test with custom key
$headers = @{
    "Content-Type" = "application/json"
    "X-Kafka-Key" = '{"orderId":"TEST-001","customerId":"CUST-001"}'
}
$body = '{"active":true,"amount":100.50,"tradeDate":"2026-03-20","createdAt":"2026-03-20T10:00:00.000Z","executionTime":36000000000,"tags":["test"],"metadata":{"source":"test"},"nestedRecord":{"someField":"test","count":1},"status":"PENDING","notes":"Test"}'

Invoke-RestMethod -Uri "http://localhost:8080/api/events/publish" -Method POST -Headers $headers -Body $body

# Test without key (auto-generated UUID)
Invoke-RestMethod -Uri "http://localhost:8080/api/events/publish" -Method POST -ContentType "application/json" -Body $body
```

### Verify in Consumer Logs
Check application logs to see the consumed message with the key:
```
Key orderId=TEST-001
Key customerId=CUST-001
```
or
```
Key messageId=550e8400-e29b-41d4-a716-446655440000
```

---

## Swagger UI

Access the interactive API documentation:
- **URL:** http://localhost:8080/swagger-ui/index.html
- **Section:** Events
- **Endpoint:** POST /api/events/publish

You can test the API directly from Swagger UI with the "Try it out" button.

---

## Notes

- The `X-Kafka-Key` header must be valid JSON
- If key is provided, it will be validated against the key schema from Schema Registry
- UUID keys use the format: `{"messageId":"<uuid-v4>"}`
- Topic is always `test-topic` (configured via `kafka.topic` property)
- All Avro field types are supported in the payload

---

## Production Recommendations

1. **Use custom keys** for messages that need specific partitioning (e.g., by customer ID, order ID)
2. **Use auto-generated keys** for messages where partition assignment doesn't matter
3. **Monitor consumer logs** to verify keys are being used correctly
4. **Set up alerts** for key validation failures
5. **Document key schema** for your team

---

## Support

For issues or questions:
- Check Swagger UI documentation
- Review application logs
- Verify Schema Registry has the correct key schema registered
- Ensure Kafka and Schema Registry are running
