package com.jobpilot.reminder;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ReminderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListGetUpdateCancelDeleteAsOwner() throws Exception {
        String token = register("reminder-owner@example.com");
        String appId = createApplication(token);

        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId":"%s",
                                  "type":"FOLLOW_UP",
                                  "title":"Follow up with recruiter",
                                  "description":"Send a polite email",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(appId, scheduledAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.title").value("Follow up with recruiter"))
                .andExpect(jsonPath("$.applicationId").value(appId))
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mockMvc.perform(get("/api/reminders")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(get("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        Instant rescheduled = scheduledAt.plus(1, ChronoUnit.DAYS);
        mockMvc.perform(patch("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Follow up again",
                                  "description":"Call instead",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(rescheduled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Follow up again"))
                .andExpect(jsonPath("$.description").value("Call instead"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(delete("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void customReminderAllowsNullApplicationId() throws Exception {
        String token = register("reminder-custom@example.com");
        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"CUSTOM",
                                  "title":"Update LinkedIn",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(scheduledAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CUSTOM"))
                .andExpect(jsonPath("$.applicationId").value(nullValue()));
    }

    @Test
    void nonCustomRequiresApplicationId() throws Exception {
        String token = register("reminder-need-app@example.com");
        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type":"FOLLOW_UP",
                                  "title":"Follow up",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(scheduledAt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotUseAnotherUsersApplication() throws Exception {
        String owner = register("reminder-app-owner@example.com");
        String other = register("reminder-app-other@example.com");
        String appId = createApplication(owner);
        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId":"%s",
                                  "type":"FOLLOW_UP",
                                  "title":"Nope",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(appId, scheduledAt)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersReminder() throws Exception {
        String owner = register("reminder-iso-owner@example.com");
        String other = register("reminder-iso-other@example.com");
        String appId = createApplication(owner);
        Instant scheduledAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId":"%s",
                                  "type":"OFFER_EXPIRY",
                                  "title":"Offer deadline",
                                  "scheduledAt":"%s"
                                }
                                """.formatted(appId, scheduledAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/reminders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(patch("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/reminders/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void authRequiredForReminders() throws Exception {
        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reminders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/reminders/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/reminders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"Reminder User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createApplication(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme","jobTitle":"Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
