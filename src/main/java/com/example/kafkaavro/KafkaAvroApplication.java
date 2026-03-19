package com.example.kafkaavro;

import com.example.kafkaavro.config.EnvValidationConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaAvroApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KafkaAvroApplication.class);
        app.addListeners(new EnvValidationConfig());
        app.run(args);
    }
}
