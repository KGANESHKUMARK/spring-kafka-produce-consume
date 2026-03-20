package com.example.kafkaavro.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cucumber step definitions for Kafka publish/consume flow
 * Tests the running application via REST API calls (SIT-level testing)
 */
public class KafkaPublishConsumeSteps {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8080";
    private ResponseEntity<Map> lastResponse;
    private String testOrderId;

    @Given("the Kafka system is running")
    public void theKafkaSystemIsRunning() {
        String url = baseUrl + "/api/kafka/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertEquals("UP", body.get("status"), "Kafka should be UP");
        System.out.println("✓ Kafka system is running");
    }

    @Given("the Schema Registry is available")
    public void theSchemaRegistryIsAvailable() {
        String url = baseUrl + "/api/registry/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertEquals("UP", body.get("status"), "Schema Registry should be UP");
        System.out.println("✓ Schema Registry is available");
    }

    @Given("I have a valid event with order ID {string}")
    public void iHaveAValidEventWithOrderID(String orderId) {
        testOrderId = orderId;
        System.out.println("✓ Created test event with order ID: " + orderId);
    }

    @When("I publish the event to topic {string}")
    public void iPublishTheEventToTopic(String topic) {
        String url = baseUrl + "/api/events";
        
        String requestBody = String.format("""
            {
              "topic": "%s",
              "contractDataKey": {
                "orderId": "%s",
                "customerId": "CUST-CUCUMBER-001"
              },
              "payload": {
                "active": true,
                "amount": 1234.56,
                "tradeDate": "2026-03-20",
                "createdAt": "2026-03-20T01:00:00.000Z",
                "executionTime": 34215123456,
                "tags": ["cucumber-test"],
                "metadata": {"source": "cucumber", "version": "1.0"},
                "nestedRecord": {"someField": "cucumber-nested", "count": 42},
                "status": "CONFIRMED",
                "notes": "Cucumber BDD test event"
              }
            }
            """, topic, testOrderId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        lastResponse = restTemplate.postForEntity(url, request, Map.class);
        System.out.println("✓ Published event to topic: " + topic);
    }

    @Then("the event should be published successfully")
    public void theEventShouldBePublishedSuccessfully() {
        assertNotNull(lastResponse, "Response should not be null");
        assertEquals(HttpStatus.OK, lastResponse.getStatusCode(), "HTTP status should be 200 OK");
        
        Map<String, Object> body = lastResponse.getBody();
        assertNotNull(body, "Response body should not be null");
        assertEquals("SUCCESS", body.get("status"), "Status should be SUCCESS");
        
        System.out.println("✓ Event published successfully: " + testOrderId);
    }

    @Then("the consumer should receive the event within {int} seconds")
    public void theConsumerShouldReceiveTheEventWithinSeconds(int seconds) throws InterruptedException {
        System.out.println("⏳ Waiting " + seconds + " seconds for consumer to process...");
        Thread.sleep(seconds * 1000L);
        
        String url = baseUrl + "/api/preview/topics/test-topic/messages?limit=10";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Preview response should not be null");
        
        Number totalMessages = (Number) body.get("totalMessages");
        assertNotNull(totalMessages, "Total messages should not be null");
        assertTrue(totalMessages.intValue() > 0, 
            "Consumer should have received messages. Total: " + totalMessages.intValue());
        
        System.out.println("✓ Consumer received messages. Total in topic: " + totalMessages.intValue());
    }

    @Then("the consumed event should have order ID {string}")
    public void theConsumedEventShouldHaveOrderID(String orderId) {
        assertEquals(testOrderId, orderId, "Order ID should match");
        System.out.println("✓ Event verified with order ID: " + orderId);
    }

    // ========== New Step Definitions for Header-Based Key API ==========

    private String testCustomerId;
    private ResponseEntity<Map> headerApiResponse;

    @Given("I have a header-based event with order ID {string} and customer ID {string}")
    public void iHaveAHeaderBasedEventWithOrderIDAndCustomerID(String orderId, String customerId) {
        testOrderId = orderId;
        testCustomerId = customerId;
        System.out.println("✓ Created header-based test event with order ID: " + orderId + ", customer ID: " + customerId);
    }

    @When("I publish the event with header key to the new endpoint")
    public void iPublishTheEventWithHeaderKeyToTheNewEndpoint() {
        String url = baseUrl + "/api/events/publish";
        
        String requestBody = String.format("""
            {
              "active": true,
              "amount": 9876.54,
              "tradeDate": "2026-03-20",
              "createdAt": "2026-03-20T10:00:00.000Z",
              "executionTime": 36000000000,
              "tags": ["header-api-test", "cucumber-bdd"],
              "metadata": {"source": "cucumber-header-test", "version": "2.0", "endpoint": "header-based"},
              "nestedRecord": {"someField": "header-test-nested", "count": 100},
              "status": "CONFIRMED",
              "notes": "Cucumber BDD test for header-based key API"
            }
            """);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Add the Kafka key as header
        String kafkaKey = String.format("{\"orderId\":\"%s\",\"customerId\":\"%s\"}", 
            testOrderId, testCustomerId);
        headers.set("X-Kafka-Key", kafkaKey);
        
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        headerApiResponse = restTemplate.postForEntity(url, request, Map.class);
        System.out.println("✓ Published event to new header-based endpoint with key: " + kafkaKey);
    }

    @Then("the event should be published successfully via header endpoint")
    public void theEventShouldBePublishedSuccessfullyViaHeaderEndpoint() {
        assertNotNull(headerApiResponse, "Response should not be null");
        assertEquals(HttpStatus.OK, headerApiResponse.getStatusCode(), "HTTP status should be 200 OK");
        
        Map<String, Object> body = headerApiResponse.getBody();
        assertNotNull(body, "Response body should not be null");
        assertEquals("SUCCESS", body.get("status"), "Status should be SUCCESS");
        
        System.out.println("✓ Event published successfully via header endpoint: " + testOrderId);
    }

    @Then("the consumer should receive the header-based event within {int} seconds")
    public void theConsumerShouldReceiveTheHeaderBasedEventWithinSeconds(int seconds) throws InterruptedException {
        System.out.println("⏳ Waiting " + seconds + " seconds for consumer to process header-based event...");
        Thread.sleep(seconds * 1000L);
        
        String url = baseUrl + "/api/preview/topics/test-topic/messages?limit=10";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "Preview response should not be null");
        
        Number totalMessages = (Number) body.get("totalMessages");
        assertNotNull(totalMessages, "Total messages should not be null");
        assertTrue(totalMessages.intValue() > 0, 
            "Consumer should have received messages. Total: " + totalMessages.intValue());
        
        System.out.println("✓ Consumer received header-based event. Total in topic: " + totalMessages.intValue());
    }
}
