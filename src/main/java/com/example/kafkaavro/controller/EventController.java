package com.example.kafkaavro.controller;

import com.example.kafkaavro.dto.EventRequest;
import com.example.kafkaavro.dto.EventResponse;
import com.example.kafkaavro.service.EventProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Kafka Avro event publishing API")
public class EventController {

    private final EventProducerService producerService;

    @Value("${kafka.topic}")
    private String defaultTopic;

    public EventController(EventProducerService producerService) {
        this.producerService = producerService;
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

    private com.example.kafkaavro.dto.ErrorResponse error(int status, String error, String message) {
        com.example.kafkaavro.dto.ErrorResponse r = new com.example.kafkaavro.dto.ErrorResponse();
        r.setStatus(status);
        r.setError(error);
        r.setMessage(message);
        r.setTimestamp(java.time.Instant.now());
        return r;
    }
}
