Feature: Kafka Event Publishing and Consuming
  Simple end-to-end test for Kafka publish and consume flow

  Scenario: Publish and consume a simple event
    Given the Kafka system is running
    And the Schema Registry is available
    And I have a valid event with order ID "ORD-CUCUMBER-001"
    When I publish the event to topic "test-topic"
    Then the event should be published successfully
    And the consumer should receive the event within 5 seconds
    And the consumed event should have order ID "ORD-CUCUMBER-001"

  Scenario: Publish and consume event with header-based key
    Given the Kafka system is running
    And the Schema Registry is available
    And I have a header-based event with order ID "ORD-HEADER-CUCUMBER-001" and customer ID "CUST-HEADER-CUCUMBER-001"
    When I publish the event with header key to the new endpoint
    Then the event should be published successfully via header endpoint
    And the consumer should receive the header-based event within 5 seconds
    And the consumed event should have order ID "ORD-HEADER-CUCUMBER-001"
