package com.example.kafkaavro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI kafkaAvroOpenAPI() {
        return new OpenAPI().info(new Info()
            .title("Kafka Avro Event API")
            .version("1.0.0")
            .description("Dynamic Kafka producer using GenericRecord with Confluent Schema Registry"));
    }
}
