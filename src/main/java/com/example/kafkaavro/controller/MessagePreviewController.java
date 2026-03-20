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
import java.util.*;

@RestController
@RequestMapping("/api/preview")
@Tag(name = "Message Preview", description = "APIs for previewing messages without consuming them")
public class MessagePreviewController {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @GetMapping("/topics/{topic}/messages")
    @Operation(
        summary = "Preview recent messages",
        description = "Preview recent messages from a topic without consuming them. Returns the last N messages."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved messages")
    @ApiResponse(responseCode = "404", description = "Topic not found")
    public ResponseEntity<?> previewMessages(
            @Parameter(description = "Topic name") 
            @PathVariable String topic,
            @Parameter(description = "Number of messages to preview (default: 10, max: 100)") 
            @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Partition number (default: 0)") 
            @RequestParam(defaultValue = "0") int partition) {
        
        if (limit > 100) {
            limit = 100;
        }
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "preview-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(topicPartition));
            
            // Get end offset
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singletonList(topicPartition));
            long endOffset = endOffsets.get(topicPartition);
            
            if (endOffset == 0) {
                return ResponseEntity.ok(Map.of(
                    "topic", topic,
                    "partition", partition,
                    "totalMessages", 0,
                    "messages", Collections.emptyList(),
                    "message", "Topic is empty"
                ));
            }
            
            // Calculate start offset
            long startOffset = Math.max(0, endOffset - limit);
            consumer.seek(topicPartition, startOffset);
            
            // Poll messages
            List<Map<String, Object>> messages = new ArrayList<>();
            var records = consumer.poll(Duration.ofSeconds(5));
            
            for (ConsumerRecord<byte[], byte[]> record : records) {
                Map<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("offset", record.offset());
                messageInfo.put("partition", record.partition());
                messageInfo.put("timestamp", record.timestamp());
                messageInfo.put("keySize", record.key() != null ? record.key().length : 0);
                messageInfo.put("valueSize", record.value() != null ? record.value().length : 0);
                messageInfo.put("hasKey", record.key() != null);
                messageInfo.put("hasValue", record.value() != null);
                messages.add(messageInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("topic", topic);
            response.put("partition", partition);
            response.put("totalMessages", messages.size());
            response.put("startOffset", startOffset);
            response.put("endOffset", endOffset);
            response.put("messages", messages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to preview messages",
                "topic", topic,
                "message", e.getMessage()
            ));
        }
    }
}
