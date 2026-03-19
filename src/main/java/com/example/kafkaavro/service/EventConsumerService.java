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

        log.info("Key orderId={}", key.get("orderId"));

        log.info("Value active={}", value.get("active"));

        log.info("Value amount={}", value.getDecimal("amount"));
        log.info("Value tradeDate={}", value.getDate("tradeDate"));
        log.info("Value createdAt={}", value.getTimestamp("createdAt"));

        log.info("Value tags={}", value.getArray("tags"));

        Map<String, Object> metadata = value.getMap("metadata");
        log.info("Value metadata.source={}", metadata.get("source"));

        SafeFieldExtractor nested = value.getNested("nestedRecord");
        if (nested != null) {
            log.info("Nested field={}", nested.get("someField"));
        }
    }
}
