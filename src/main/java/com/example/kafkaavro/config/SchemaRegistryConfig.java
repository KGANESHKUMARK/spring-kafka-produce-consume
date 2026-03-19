package com.example.kafkaavro.config;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SchemaRegistryConfig {

    @Bean
    public SchemaRegistryClient schemaRegistryClient(
            @Value("${KAFKA_SCHEMA_REGISTRY_URL}") String url,
            @Value("${KAFKA_SCHEMA_REGISTRY_USERNAME}") String username,
            @Value("${KAFKA_SCHEMA_REGISTRY_PASSWORD}") String password) {

        Map<String, Object> config = new HashMap<>();
        config.put("basic.auth.credentials.source", "USER_INFO");
        config.put("basic.auth.user.info", username + ":" + password);
        config.put("auto.register.schemas", "false");
        config.put("use.latest.version", "true");

        return new CachedSchemaRegistryClient(List.of(url), 100, config);
    }
}
