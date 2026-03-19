package com.example.kafkaavro.web;

import com.example.kafkaavro.controller.EventController;
import com.example.kafkaavro.dto.EventResponse;
import com.example.kafkaavro.exception.DataConversionException;
import com.example.kafkaavro.exception.SchemaFetchException;
import com.example.kafkaavro.schema.SchemaRegistryService;
import com.example.kafkaavro.service.EventProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@ActiveProfiles("test")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventProducerService producerService;

    @MockBean
    private SchemaRegistryClient schemaRegistryClient;

    @MockBean
    private SchemaRegistryService schemaRegistryService;

    private static final String VALID_REQUEST = """
        {
          "topic": "order-topic",
          "contractDataKey": { "orderId": "ORD-001" },
          "payload": {
            "amount": "123.45",
            "tradeDate": "2026-03-19",
            "createdAt": "2026-03-19T10:00:00Z",
            "active": true
          }
        }
        """;

    @Test
    void publishEvent_validRequest_shouldReturn200() throws Exception {
        EventResponse mockResponse = EventResponse.builder()
            .status("SUCCESS").topic("order-topic")
            .message("Event published successfully").timestamp(Instant.now())
            .build();
        when(producerService.send(any(), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.topic").value("order-topic"));
    }


    @Test
    void publishEvent_noTopicInRequest_shouldFallbackToDefaultTopic() throws Exception {
        String request = "{\"contractDataKey\":{\"orderId\":\"X\"},\"payload\":{\"active\":true}}";
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        when(producerService.send(topicCaptor.capture(), any(), any()))
            .thenReturn(EventResponse.builder().status("SUCCESS").build());

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk());

        assertThat(topicCaptor.getValue()).isEqualTo("test-topic");
    }

    @Test
    void publishEvent_schemaFetchException_shouldReturn500() throws Exception {
        when(producerService.send(any(), any(), any()))
            .thenThrow(new SchemaFetchException("order-topic-value",
                new RuntimeException("Registry unreachable")));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("Schema Registry Error"))
            .andExpect(jsonPath("$.message").value(containsString("order-topic-value")));
    }

    @Test
    void publishEvent_dataConversionException_shouldReturn400() throws Exception {
        when(producerService.send(any(), any(), any()))
            .thenThrow(new DataConversionException("amount", "BYTES", "bad-value", null));

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Data Conversion Error"));
    }
}
