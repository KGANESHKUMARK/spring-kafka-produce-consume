package com.example.kafkaavro.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Minimal Cucumber Spring configuration
 * This enables Cucumber to work with Spring without loading full application context
 */
@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = CucumberConfig.class
)
public class CucumberConfig {
    // Empty configuration class - just enables Cucumber + Spring integration
    // Tests use REST calls to running application, not Spring beans
}
