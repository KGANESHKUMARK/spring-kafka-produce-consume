package com.example.kafkaavro.integration;

import com.example.kafkaavro.util.TestSchemaFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(SpringExtension.class)
class EventProducerIntegrationTest {

    @Container
    static KafkaContainer kafkaContainer =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideKafkaBootstrap(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }

    @TestConfiguration
    static class TestSchemaRegistryConfig {
        static final MockSchemaRegistryClient mockClient = new MockSchemaRegistryClient();
        @Bean
        @Primary
        public SchemaRegistryClient schemaRegistryClient() {
            return mockClient;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static Schema keySchema;
    private static Schema valueSchema;

    @BeforeAll
    static void registerTestSchemas() throws Exception {
        keySchema = TestSchemaFactory.buildOrderKeySchema();
        valueSchema = TestSchemaFactory.buildOrderValueSchema();
        TestSchemaRegistryConfig.mockClient.register("test-topic-key",
            new AvroSchema(keySchema.toString()));
        TestSchemaRegistryConfig.mockClient.register("test-topic-value",
            new AvroSchema(valueSchema.toString()));
    }

    @Test
    void publishEvent_shouldProduceGenericRecordToKafka() throws Exception {
        String request = """
            {
              "contractDataKey": { "orderId": "ORD-INT-001" },
              "payload": {
                "amount": "99.9900",
                "tradeDate": "2026-03-19",
                "createdAt": "2026-03-19T08:00:00Z",
                "timeField": "08:00:00",
                "active": true,
                "tags": ["test"],
                "metadata": { "source": "integration", "region": "SG" },
                "description": null
              }
            }
            """;

        mockMvc.perform(post("/api/events")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaContainer.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-verify-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
        consumerProps.put("schema.registry.url", "mock://test");
        consumerProps.put("specific.avro.reader", "false");

        try (KafkaConsumer<GenericRecord, GenericRecord> consumer =
                new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("test-topic"));
            ConsumerRecords<GenericRecord, GenericRecord> records =
                consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThan(0);
            ConsumerRecord<GenericRecord, GenericRecord> consumed =
                records.iterator().next();

            assertThat(consumed.key().get("orderId").toString()).isEqualTo("ORD-INT-001");
            assertThat(consumed.value().get("active")).isEqualTo(true);
        }
    }

    @Test
    void publishEvent_nullPayload_shouldReturn400AndNotProduce() throws Exception {
        String request = "{\"contractDataKey\":{\"orderId\":\"X\"},\"payload\":null}";

        mockMvc.perform(post("/api/events")
                .contentType("application/json")
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void poisonPill_shouldRouteToDeadLetterTopic() throws Exception {
        String sourceTopic = "test-topic";
        String dltTopic = sourceTopic + ".DLT";

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaContainer.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(sourceTopic,
                "bad-key".getBytes(), "not-valid-avro".getBytes())).get();
        }

        Map<String, Object> dltConsumerProps = new HashMap<>();
        dltConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaContainer.getBootstrapServers());
        dltConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verify-group");
        dltConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dltConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            ByteArrayDeserializer.class);
        dltConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            ByteArrayDeserializer.class);

        AtomicInteger dltCount = new AtomicInteger(0);

        try (KafkaConsumer<byte[], byte[]> dltConsumer = new KafkaConsumer<>(dltConsumerProps)) {
            dltConsumer.subscribe(List.of(dltTopic));

            Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ConsumerRecords<byte[], byte[]> records =
                        dltConsumer.poll(Duration.ofMillis(500));
                    dltCount.addAndGet(records.count());
                    assertThat(dltCount.get()).isGreaterThan(0);
                });
        }
    }
}
