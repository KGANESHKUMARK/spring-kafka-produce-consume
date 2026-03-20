package com.example.kafkaavro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitoring")
@Tag(name = "Consumer Monitoring", description = "APIs for monitoring consumer lag and performance")
public class ConsumerLagController {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    private AdminClient getAdminClient() {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @GetMapping("/consumer-groups/{groupId}/lag")
    @Operation(
        summary = "Get consumer lag",
        description = "Returns consumer lag for each partition, showing how far behind the consumer is"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved lag information")
    @ApiResponse(responseCode = "404", description = "Consumer group not found")
    public ResponseEntity<?> getConsumerLag(
            @Parameter(description = "Consumer group ID") 
            @PathVariable String groupId) {
        
        try (AdminClient adminClient = getAdminClient()) {
            // Get consumer group offsets
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();
            
            if (offsets.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Consumer group not found or has no offsets",
                    "groupId", groupId
                ));
            }
            
            // Get log end offsets
            Map<TopicPartition, Long> endOffsets = new HashMap<>();
            Set<TopicPartition> partitions = offsets.keySet();
            
            for (TopicPartition tp : partitions) {
                ListOffsetsResult.ListOffsetsResultInfo info = adminClient
                    .listOffsets(Map.of(tp, OffsetSpec.latest()))
                    .all()
                    .get()
                    .get(tp);
                endOffsets.put(tp, info.offset());
            }
            
            // Calculate lag
            long totalLag = 0;
            List<Map<String, Object>> partitionLags = new ArrayList<>();
            
            for (TopicPartition tp : partitions) {
                long currentOffset = offsets.get(tp).offset();
                long logEndOffset = endOffsets.get(tp);
                long lag = logEndOffset - currentOffset;
                totalLag += lag;
                
                Map<String, Object> partitionInfo = new HashMap<>();
                partitionInfo.put("partition", tp.partition());
                partitionInfo.put("topic", tp.topic());
                partitionInfo.put("currentOffset", currentOffset);
                partitionInfo.put("logEndOffset", logEndOffset);
                partitionInfo.put("lag", lag);
                partitionLags.add(partitionInfo);
            }
            
            // Determine status
            String status;
            String message;
            if (totalLag == 0) {
                status = "HEALTHY";
                message = "Consumer is up to date";
            } else if (totalLag < 100) {
                status = "GOOD";
                message = "Consumer has minimal lag";
            } else if (totalLag < 1000) {
                status = "WARNING";
                message = "Consumer is lagging behind by " + totalLag + " messages";
            } else {
                status = "CRITICAL";
                message = "Consumer has significant lag: " + totalLag + " messages";
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("groupId", groupId);
            response.put("totalLag", totalLag);
            response.put("status", status);
            response.put("message", message);
            response.put("partitions", partitionLags);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve consumer lag",
                "groupId", groupId,
                "message", e.getMessage()
            ));
        }
    }
}
