package com.example.kafkaavro.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnvValidationConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final List<String> REQUIRED_VARS = List.of(
        "KAFKA_BOOTSTRAP_SERVERS", "KAFKA_SCHEMA_REGISTRY_URL",
        "KAFKA_USERNAME", "KAFKA_PASSWORD",
        "KAFKA_SCHEMA_REGISTRY_USERNAME", "KAFKA_SCHEMA_REGISTRY_PASSWORD",
        "KAFKA_TOPIC", "KAFKA_CONSUMER_GROUP"
    );

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();

        String[] activeProfiles = env.getActiveProfiles();
        if (Arrays.asList(activeProfiles).contains("test")) return;
        if ("false".equalsIgnoreCase(env.getProperty("app.env-validation.enabled"))) return;

        List<String> missing = REQUIRED_VARS.stream()
            .filter(var -> !StringUtils.hasText(env.getProperty(var)))
            .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Application startup failed. Missing required environment variables: " + missing);
        }
    }
}
