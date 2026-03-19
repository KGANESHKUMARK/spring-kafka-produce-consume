package com.example.kafkaavro.web;

import com.example.kafkaavro.controller.EventController;
import com.example.kafkaavro.service.EventProducerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@ActiveProfiles("test")
class EventControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventProducerService producerService;

    @MockBean
    private io.confluent.kafka.schemaregistry.client.SchemaRegistryClient schemaRegistryClient;

    @MockBean
    private com.example.kafkaavro.schema.SchemaRegistryService schemaRegistryService;

    @Test
    void publishEvent_nullPayload_shouldReturn400() throws Exception {
        String request = "{\"contractDataKey\":{\"orderId\":\"ORD-001\"},\"payload\":null}";

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("payload must not be null"));
        
        verifyNoInteractions(producerService);
    }

    @Test
    void publishEvent_nullContractDataKey_shouldReturn400() throws Exception {
        String request = "{\"contractDataKey\":null,\"payload\":{\"active\":true}}";

        mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("contractDataKey must not be null"));
        
        verifyNoInteractions(producerService);
    }
}
