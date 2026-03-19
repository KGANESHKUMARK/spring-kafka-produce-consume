package com.example.kafkaavro.service;

import com.example.kafkaavro.dto.EventResponse;
import com.example.kafkaavro.mapper.GenericRecordBuilder;
import com.example.kafkaavro.schema.SchemaRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class EventProducerService {

    private final KafkaTemplate<GenericRecord, GenericRecord> kafkaTemplate;
    private final SchemaRegistryService schemaRegistryService;
    private final GenericRecordBuilder genericRecordBuilder;

    public EventProducerService(
            KafkaTemplate<GenericRecord, GenericRecord> kafkaTemplate,
            SchemaRegistryService schemaRegistryService,
            GenericRecordBuilder genericRecordBuilder) {
        this.kafkaTemplate = kafkaTemplate;
        this.schemaRegistryService = schemaRegistryService;
        this.genericRecordBuilder = genericRecordBuilder;
    }

    public EventResponse send(String topic, JsonNode keyJson, JsonNode valueJson) {
        Schema keySchema = schemaRegistryService.getKeySchema(topic);
        Schema valueSchema = schemaRegistryService.getValueSchema(topic);

        GenericRecord keyRecord = genericRecordBuilder.build(keySchema, keyJson);
        GenericRecord valueRecord = genericRecordBuilder.build(valueSchema, valueJson);

        ProducerRecord<GenericRecord, GenericRecord> producerRecord =
            new ProducerRecord<>(topic, keyRecord, valueRecord);

        try {
            kafkaTemplate.send(producerRecord).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Kafka send timed out for topic: " + topic, e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Kafka send failed for topic: " + topic, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Kafka send interrupted for topic: " + topic, e);
        }

        return EventResponse.builder()
            .status("SUCCESS")
            .topic(topic)
            .message("Event published successfully")
            .timestamp(Instant.now())
            .build();
    }
}
