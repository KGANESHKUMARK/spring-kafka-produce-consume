package com.example.kafkaavro.controller;

import com.example.kafkaavro.dto.EventRequest;
import com.example.kafkaavro.dto.EventResponse;
import com.example.kafkaavro.service.EventProducerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Kafka Avro event publishing API")
public class EventController {

    private final EventProducerService producerService;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic}")
    private String defaultTopic;

    public EventController(EventProducerService producerService, ObjectMapper objectMapper) {
        this.producerService = producerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Publish an event to Kafka")
    @ApiResponse(responseCode = "200", description = "Event published successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request — null payload or contractDataKey")
    @ApiResponse(responseCode = "500", description = "Schema fetch failure or Kafka send error")
    public ResponseEntity<?> publish(@RequestBody EventRequest request) {
        if (request.getContractDataKey() == null || request.getContractDataKey().isNull()) {
            return ResponseEntity.badRequest().body(
                error(400, "Bad Request", "contractDataKey must not be null"));
        }
        if (request.getPayload() == null || request.getPayload().isNull()) {
            return ResponseEntity.badRequest().body(
                error(400, "Bad Request", "payload must not be null"));
        }
        String topic = (request.getTopic() != null && !request.getTopic().isBlank())
            ? request.getTopic()
            : defaultTopic;
        return ResponseEntity.ok(
            producerService.send(topic, request.getContractDataKey(), request.getPayload()));
    }

    @PostMapping("/publish")
    @Operation(
        summary = "Publish an event to Kafka with key in header",
        description = "New endpoint that accepts Kafka key via X-Kafka-Key header (optional). " +
                      "If key is not provided, a random UUID will be generated. " +
                      "Body contains only the payload data."
    )
    @ApiResponse(responseCode = "200", description = "Event published successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request — null payload or invalid key JSON")
    @ApiResponse(responseCode = "500", description = "Schema fetch failure or Kafka send error")
    public ResponseEntity<?> publishWithHeaderKey(
            @Parameter(description = "Kafka message key as JSON string (optional). If not provided, UUID will be generated.", 
                       example = "{\"orderId\":\"ORD-12345\",\"customerId\":\"CUST-67890\"}")
            @RequestHeader(value = "X-Kafka-Key", required = false) String kafkaKeyHeader,
            @Parameter(description = "Event payload data")
            @RequestBody JsonNode payload) {
        
        if (payload == null || payload.isNull()) {
            return ResponseEntity.badRequest().body(
                error(400, "Bad Request", "payload must not be null"));
        }

        JsonNode key;
        try {
            if (kafkaKeyHeader != null && !kafkaKeyHeader.isBlank()) {
                // Parse the JSON key from header
                key = objectMapper.readTree(kafkaKeyHeader);
            } else {
                // Generate UUID-based key matching the Avro key schema (orderId + customerId)
                ObjectNode uuidKey = objectMapper.createObjectNode();
                uuidKey.put("orderId", "AUTO-" + UUID.randomUUID().toString());
                uuidKey.putNull("customerId");
                key = uuidKey;
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                error(400, "Bad Request", "Invalid JSON in X-Kafka-Key header: " + e.getMessage()));
        }

        return ResponseEntity.ok(producerService.send(defaultTopic, key, payload));
    }

    private com.example.kafkaavro.dto.ErrorResponse error(int status, String error, String message) {
        com.example.kafkaavro.dto.ErrorResponse r = new com.example.kafkaavro.dto.ErrorResponse();
        r.setStatus(status);
        r.setError(error);
        r.setMessage(message);
        r.setTimestamp(java.time.Instant.now());
        return r;
    }
}
