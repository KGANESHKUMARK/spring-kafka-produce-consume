package com.example.kafkaavro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/dlt")
@Tag(name = "Dead Letter Queue", description = "APIs for inspecting dead letter topic messages")
public class DLTInspectorController {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @GetMapping("/{topic}/messages")
    @Operation(
        summary = "Inspect DLT messages",
        description = "Retrieves messages from the dead letter topic for inspection and analysis"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved DLT messages")
    public ResponseEntity<?> inspectDLTMessages(
            @Parameter(description = "Original topic name (DLT topic will be {topic}.DLT)") 
            @PathVariable String topic,
            @Parameter(description = "Number of messages to retrieve (default: 10, max: 100)") 
            @RequestParam(defaultValue = "10") int limit) {
        
        if (limit > 100) {
            limit = 100;
        }
        
        String dltTopic = topic + ".DLT";
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-inspector-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            TopicPartition topicPartition = new TopicPartition(dltTopic, 0);
            consumer.assign(Collections.singletonList(topicPartition));
            
            // Get total messages
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singletonList(topicPartition));
            long endOffset = endOffsets.get(topicPartition);
            
            if (endOffset == 0) {
                return ResponseEntity.ok(Map.of(
                    "dltTopic", dltTopic,
                    "originalTopic", topic,
                    "totalMessages", 0,
                    "messages", Collections.emptyList(),
                    "message", "No messages in DLT"
                ));
            }
            
            // Seek to beginning
            consumer.seekToBeginning(Collections.singletonList(topicPartition));
            
            // Poll messages
            List<Map<String, Object>> messages = new ArrayList<>();
            var records = consumer.poll(Duration.ofSeconds(5));
            
            int count = 0;
            for (ConsumerRecord<byte[], byte[]> record : records) {
                if (count >= limit) break;
                
                Map<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("offset", record.offset());
                messageInfo.put("partition", record.partition());
                messageInfo.put("timestamp", Instant.ofEpochMilli(record.timestamp()).toString());
                messageInfo.put("keySize", record.key() != null ? record.key().length : 0);
                messageInfo.put("valueSize", record.value() != null ? record.value().length : 0);
                
                // Extract headers for failure information
                Map<String, String> headers = new HashMap<>();
                record.headers().forEach(header -> {
                    headers.put(header.key(), new String(header.value()));
                });
                messageInfo.put("headers", headers);
                
                // Try to extract failure reason from headers
                if (headers.containsKey("kafka_dlt-exception-message")) {
                    messageInfo.put("failureReason", headers.get("kafka_dlt-exception-message"));
                }
                if (headers.containsKey("kafka_dlt-original-topic")) {
                    messageInfo.put("originalTopic", headers.get("kafka_dlt-original-topic"));
                }
                
                messages.add(messageInfo);
                count++;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("dltTopic", dltTopic);
            response.put("originalTopic", topic);
            response.put("totalMessages", endOffset);
            response.put("retrievedMessages", messages.size());
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to inspect DLT messages",
                "dltTopic", dltTopic,
                "message", e.getMessage()
            ));
        }
    }
}
