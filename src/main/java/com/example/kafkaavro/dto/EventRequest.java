package com.example.kafkaavro.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public class EventRequest {

    private String topic;

    @NotNull(message = "contractDataKey must not be null")
    private JsonNode contractDataKey;

    @NotNull(message = "payload must not be null")
    private JsonNode payload;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public JsonNode getContractDataKey() {
        return contractDataKey;
    }

    public void setContractDataKey(JsonNode contractDataKey) {
        this.contractDataKey = contractDataKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
