# Kafka Avro Enterprise Streaming System

## 1. Project Overview

This project is a production-grade Spring Boot application for dynamic Kafka event streaming using Avro (GenericRecord only) and Confluent Schema Registry. It supports dynamic schema fetching (no codegen), logical types, nested records, arrays, and maps. All configuration is via environment variables. The system is fully DLT-enabled and supports schema evolution without restarts.

**Architecture:**
- Spring Boot 3.2.x, Java 17
- Kafka producer/consumer using GenericRecord (no SpecificRecord)
- Dynamic schema resolution from Schema Registry (always latest)
- REST API for publishing events
- DLT (Dead Letter Topic) routing for all deserialization/conversion errors
- Testcontainers-based integration tests

---

## 2. Environment Variables

| Variable | Required | Description |
|---|---|---|
| KAFKA_BOOTSTRAP_SERVERS | Yes | Kafka broker address |
| KAFKA_SCHEMA_REGISTRY_URL | Yes | Confluent Schema Registry URL |
| KAFKA_USERNAME | Yes | Kafka SASL username |
| KAFKA_PASSWORD | Yes | Kafka SASL password — NEVER log this |
| KAFKA_SCHEMA_REGISTRY_USERNAME | Yes | Schema Registry basic auth username |
| KAFKA_SCHEMA_REGISTRY_PASSWORD | Yes | Schema Registry basic auth password — NEVER log this |
| KAFKA_TOPIC | Yes | Default Kafka topic |
| KAFKA_CONSUMER_GROUP | Yes | Kafka consumer group ID |

---

## 3. Local Setup

1. **Start Kafka + Schema Registry:**
   ```sh
   docker-compose up -d
   ```
2. **Register schemas manually:**
   - Using Confluent CLI or curl. Example:
     ```sh
     curl -X POST -u $KAFKA_SCHEMA_REGISTRY_USERNAME:$KAFKA_SCHEMA_REGISTRY_PASSWORD \
       -H "Content-Type: application/vnd.schemaregistry.v1+json" \
       --data '{"schema": "<AVRO_SCHEMA_JSON>"}' \
       http://localhost:8081/subjects/<topic>-key/versions
     ```
3. **Set all 8 environment variables** (see table above).
4. **Build and run:**
   ```sh
   mvn clean install
   mvn spring-boot:run
   ```

---

## 4. Example curl for POST /api/events

```sh
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "contractDataKey": { "orderId": "ORD-001" },
    "payload": {
      "amount": "123.45",
      "tradeDate": "2026-03-19",
      "createdAt": "2026-03-19T10:00:00Z",
      "active": true
    }
  }'
```

---

## 5. Schema Evolution Guide

- The app **never registers schemas**; it always fetches the latest version from Schema Registry at runtime.
- Caching is used for performance. If a schema changes in the registry, the cache is invalidated on application restart.
- For hot reload, restart the app to pick up new schema versions.
- Both key and value schemas are fetched dynamically per topic using the subject naming convention `<topic>-key` and `<topic>-value`.

---

## 6. Dead Letter Topic Guide

- **Retries:** Each failed message is retried 3 times (1 second apart).
- **DLT Routing:** After retries, failed records are routed to `{original-topic}.DLT` partition 0.
- **How to consume from DLT:**
  - Use any Kafka consumer to read from the DLT topic (e.g., `test-topic.DLT`).
- **ErrorHandlingDeserializer:**
  - All consumer deserialization errors (including Avro errors) are routed to DLT instead of crashing the consumer.
  - DLT records contain the original bytes and error metadata.

---
