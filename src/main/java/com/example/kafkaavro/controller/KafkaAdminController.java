package com.example.kafkaavro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kafka")
@Tag(name = "Kafka Admin", description = "APIs to verify and inspect Kafka cluster, topics, and consumer groups")
public class KafkaAdminController {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    private AdminClient getAdminClient() {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @GetMapping("/health")
    @Operation(summary = "Kafka cluster health check", description = "Checks if Kafka cluster is accessible")
    @ApiResponse(responseCode = "200", description = "Kafka is healthy")
    @ApiResponse(responseCode = "503", description = "Kafka is unavailable")
    public ResponseEntity<?> healthCheck() {
        try (AdminClient adminClient = getAdminClient()) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get();
            int nodeCount = clusterResult.nodes().get().size();
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("clusterId", clusterId);
            health.put("nodeCount", nodeCount);
            health.put("accessible", true);
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("accessible", false);
            health.put("error", e.getMessage());
            
            return ResponseEntity.status(503).body(health);
        }
    }

    @GetMapping("/cluster")
    @Operation(summary = "Get cluster information", description = "Returns detailed Kafka cluster information")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved cluster info")
    public ResponseEntity<?> getClusterInfo() {
        try (AdminClient adminClient = getAdminClient()) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            
            Map<String, Object> clusterInfo = new HashMap<>();
            clusterInfo.put("clusterId", clusterResult.clusterId().get());
            clusterInfo.put("controller", clusterResult.controller().get().toString());
            clusterInfo.put("nodes", clusterResult.nodes().get().stream()
                .map(node -> Map.of(
                    "id", node.id(),
                    "host", node.host(),
                    "port", node.port(),
                    "rack", node.rack() != null ? node.rack() : "N/A"
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(clusterInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve cluster info",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/topics")
    @Operation(summary = "List all topics", description = "Returns all topics in the Kafka cluster")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved topics")
    public ResponseEntity<?> listTopics() {
        try (AdminClient adminClient = getAdminClient()) {
            ListTopicsResult topicsResult = adminClient.listTopics();
            Set<String> topics = topicsResult.names().get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalTopics", topics.size());
            response.put("topics", topics);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve topics",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/topics/{topic}")
    @Operation(summary = "Describe topic", description = "Returns detailed information about a specific topic including partitions and replicas")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved topic info")
    @ApiResponse(responseCode = "404", description = "Topic not found")
    public ResponseEntity<?> describeTopic(
            @Parameter(description = "Topic name") 
            @PathVariable String topic) {
        try (AdminClient adminClient = getAdminClient()) {
            DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singleton(topic));
            TopicDescription description = topicsResult.allTopicNames().get().get(topic);
            
            if (description == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Topic not found",
                    "topic", topic
                ));
            }
            
            Map<String, Object> topicInfo = new HashMap<>();
            topicInfo.put("name", description.name());
            topicInfo.put("internal", description.isInternal());
            topicInfo.put("partitionCount", description.partitions().size());
            topicInfo.put("partitions", description.partitions().stream()
                .map(partition -> Map.of(
                    "partition", partition.partition(),
                    "leader", partition.leader() != null ? partition.leader().id() : -1,
                    "replicas", partition.replicas().stream()
                        .map(node -> node.id())
                        .collect(Collectors.toList()),
                    "isr", partition.isr().stream()
                        .map(node -> node.id())
                        .collect(Collectors.toList())
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(topicInfo);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Topic not found",
                    "topic", topic
                ));
            }
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to describe topic",
                "topic", topic,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to describe topic",
                "topic", topic,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/consumer-groups")
    @Operation(summary = "List consumer groups", description = "Returns all consumer groups in the Kafka cluster")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved consumer groups")
    public ResponseEntity<?> listConsumerGroups() {
        try (AdminClient adminClient = getAdminClient()) {
            ListConsumerGroupsResult groupsResult = adminClient.listConsumerGroups();
            Collection<ConsumerGroupListing> groups = groupsResult.all().get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalGroups", groups.size());
            response.put("groups", groups.stream()
                .map(group -> Map.of(
                    "groupId", group.groupId(),
                    "isSimpleConsumerGroup", group.isSimpleConsumerGroup(),
                    "state", group.state().isPresent() ? group.state().get().toString() : "UNKNOWN"
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve consumer groups",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/consumer-groups/{groupId}")
    @Operation(summary = "Describe consumer group", description = "Returns detailed information about a specific consumer group")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved consumer group info")
    @ApiResponse(responseCode = "404", description = "Consumer group not found")
    public ResponseEntity<?> describeConsumerGroup(
            @Parameter(description = "Consumer group ID") 
            @PathVariable String groupId) {
        try (AdminClient adminClient = getAdminClient()) {
            DescribeConsumerGroupsResult groupsResult = adminClient.describeConsumerGroups(Collections.singleton(groupId));
            ConsumerGroupDescription description = groupsResult.all().get().get(groupId);
            
            if (description == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Consumer group not found",
                    "groupId", groupId
                ));
            }
            
            Map<String, Object> groupInfo = new HashMap<>();
            groupInfo.put("groupId", description.groupId());
            groupInfo.put("isSimpleConsumerGroup", description.isSimpleConsumerGroup());
            groupInfo.put("state", description.state().toString());
            groupInfo.put("coordinator", Map.of(
                "id", description.coordinator().id(),
                "host", description.coordinator().host(),
                "port", description.coordinator().port()
            ));
            groupInfo.put("members", description.members().stream()
                .map(member -> Map.of(
                    "memberId", member.consumerId(),
                    "clientId", member.clientId(),
                    "host", member.host(),
                    "assignment", member.assignment().topicPartitions().stream()
                        .map(tp -> tp.topic() + "-" + tp.partition())
                        .collect(Collectors.toList())
                ))
                .collect(Collectors.toList()));
            
            return ResponseEntity.ok(groupInfo);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.GroupIdNotFoundException) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Consumer group not found",
                    "groupId", groupId
                ));
            }
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to describe consumer group",
                "groupId", groupId,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to describe consumer group",
                "groupId", groupId,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/consumer-groups/{groupId}/offsets")
    @Operation(summary = "Get consumer group offsets", description = "Returns the current offsets for all partitions consumed by the group")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved offsets")
    @ApiResponse(responseCode = "404", description = "Consumer group not found")
    public ResponseEntity<?> getConsumerGroupOffsets(
            @Parameter(description = "Consumer group ID") 
            @PathVariable String groupId) {
        try (AdminClient adminClient = getAdminClient()) {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> offsets = offsetsResult.partitionsToOffsetAndMetadata().get();
            
            Map<String, Object> response = new HashMap<>();
            response.put("groupId", groupId);
            response.put("offsets", offsets.entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> entry.getKey().topic() + "-" + entry.getKey().partition(),
                    entry -> Map.of(
                        "offset", entry.getValue().offset(),
                        "metadata", entry.getValue().metadata() != null ? entry.getValue().metadata() : ""
                    )
                )));
            
            return ResponseEntity.ok(response);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.GroupIdNotFoundException) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Consumer group not found",
                    "groupId", groupId
                ));
            }
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve offsets",
                "groupId", groupId,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve offsets",
                "groupId", groupId,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/summary")
    @Operation(summary = "Get Kafka cluster summary", description = "Returns a comprehensive summary of the Kafka cluster including topics and consumer groups")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved summary")
    public ResponseEntity<?> getSummary() {
        try (AdminClient adminClient = getAdminClient()) {
            // Get cluster info
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get();
            int nodeCount = clusterResult.nodes().get().size();
            
            // Get topics
            ListTopicsResult topicsResult = adminClient.listTopics();
            Set<String> topics = topicsResult.names().get();
            
            // Get consumer groups
            ListConsumerGroupsResult groupsResult = adminClient.listConsumerGroups();
            Collection<ConsumerGroupListing> groups = groupsResult.all().get();
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("cluster", Map.of(
                "clusterId", clusterId,
                "nodeCount", nodeCount
            ));
            summary.put("topics", Map.of(
                "totalTopics", topics.size(),
                "topicNames", topics
            ));
            summary.put("consumerGroups", Map.of(
                "totalGroups", groups.size(),
                "groupIds", groups.stream()
                    .map(ConsumerGroupListing::groupId)
                    .collect(Collectors.toList())
            ));
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to retrieve summary",
                "message", e.getMessage()
            ));
        }
    }
}
