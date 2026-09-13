package com.jobpilot.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.event.dto.ApplicationStatusChangedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void createAndStatusChangeInsertPendingOutboxRows() throws Exception {
        long before = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);

        String token = register("outbox-owner@example.com", "password123", "Outbox Owner");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company":"Acme",
                                  "jobTitle":"Engineer",
                                  "status":"SAVED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID applicationId = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();

        List<OutboxEvent> afterCreate = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("APPLICATION", applicationId);
        assertThat(afterCreate).hasSize(1);
        OutboxEvent createEvent = afterCreate.getFirst();
        assertThat(createEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(createEvent.getEventType()).isEqualTo(ApplicationStatusChangedEvent.EVENT_TYPE);
        assertThat(createEvent.getTopic()).isEqualTo("jobpilot.application.events");
        assertThat(createEvent.getPartitionKey()).isEqualTo(applicationId.toString());

        ApplicationStatusChangedEvent createPayload =
                objectMapper.readValue(createEvent.getPayload(), ApplicationStatusChangedEvent.class);
        assertThat(createPayload.applicationId()).isEqualTo(applicationId);
        assertThat(createPayload.fromStatus()).isNull();
        assertThat(createPayload.toStatus()).isEqualTo("SAVED");

        mockMvc.perform(patch("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "status": "APPLIED"
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        List<OutboxEvent> afterPatch = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("APPLICATION", applicationId);
        assertThat(afterPatch).hasSize(2);
        assertThat(afterPatch).allMatch(e -> e.getStatus() == OutboxEventStatus.PENDING);

        ApplicationStatusChangedEvent patchPayload =
                objectMapper.readValue(afterPatch.get(1).getPayload(), ApplicationStatusChangedEvent.class);
        assertThat(patchPayload.fromStatus()).isEqualTo("SAVED");
        assertThat(patchPayload.toStatus()).isEqualTo("APPLIED");

        assertThat(outboxEventRepository.countByStatus(OutboxEventStatus.PENDING))
                .isEqualTo(before + 2);
    }

    private String register(String email, String password, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s",
                                  "name":"%s"
                                }
                                """.formatted(email, password, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
