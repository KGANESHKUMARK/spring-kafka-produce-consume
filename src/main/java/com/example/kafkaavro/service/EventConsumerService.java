package com.example.kafkaavro.service;

import com.example.kafkaavro.util.SafeFieldExtractor;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventConsumerService {

    private static final Logger log = LoggerFactory.getLogger(EventConsumerService.class);

    @KafkaListener(
        topics = "${kafka.topic}",
        groupId = "${KAFKA_CONSUMER_GROUP}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<GenericRecord, GenericRecord> record) {
        log.info("Consumed message from topic={} partition={} offset={}",
            record.topic(), record.partition(), record.offset());

        SafeFieldExtractor key = new SafeFieldExtractor(record.key());
        SafeFieldExtractor value = new SafeFieldExtractor(record.value());

        log.info("=== Key Fields ===");
        log.info("Key orderId={}", key.get("orderId"));
        log.info("Key customerId={}", key.get("customerId"));

        log.info("=== Value Fields ===");
        log.info("Value active={}", value.get("active"));
        log.info("Value amount={}", value.getDecimal("amount"));
        log.info("Value tradeDate={}", value.getDate("tradeDate"));
        log.info("Value createdAt={}", value.getTimestamp("createdAt"));
        log.info("Value executionTime={}", value.getTime("executionTime"));
        log.info("Value status={}", value.get("status"));
        log.info("Value notes={}", value.get("notes"));

        log.info("=== Array Field ===");
        log.info("Value tags={}", value.getArray("tags"));

        log.info("=== Map Field ===");
        Map<String, Object> metadata = value.getMap("metadata");
        if (metadata != null && !metadata.isEmpty()) {
            log.info("Value metadata.source={}", metadata.get("source"));
            log.info("Value metadata.version={}", metadata.get("version"));
        }

        log.info("=== Nested Record ===");
        SafeFieldExtractor nested = value.getNested("nestedRecord");
        if (nested != null) {
            log.info("Nested someField={}", nested.get("someField"));
            log.info("Nested count={}", nested.get("count"));
        }
    }
}
