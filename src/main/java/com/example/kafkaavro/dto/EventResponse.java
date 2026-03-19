package com.example.kafkaavro.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class EventResponse {
    private String status;
    private String topic;
    private String message;
    private Instant timestamp;
}
