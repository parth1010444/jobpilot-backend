package com.jobpilot.recommendation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleAppliedYieldsFollowUpAndListIsSortedByPriority() throws Exception {
        String token = register("rec-owner@example.com");

        String offerId = createApplication(token, "Offer Co", "OFFER");

        String appliedId = createApplication(token, "Stale Apply Co", "APPLIED");
        backdateUpdatedAt(appliedId, Instant.now().minus(10, ChronoUnit.DAYS));

        String savedId = createApplication(token, "Old Saved Co", "SAVED");
        backdateUpdatedAt(savedId, Instant.now().minus(20, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/applications/{id}/recommendation", appliedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.title").value("Follow up with recruiter"))
                .andExpect(jsonPath("$.applicationId").value(appliedId));

        mockMvc.perform(get("/api/applications/{id}/recommendation", offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("EVALUATE_OFFER"))
                .andExpect(jsonPath("$.priority").value("HIGH"));

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[1].priority").value("HIGH"))
                .andExpect(jsonPath("$[2].action").value("APPLY_OR_ARCHIVE"))
                .andExpect(jsonPath("$[2].priority").value("MEDIUM"));

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].priority").value("HIGH"));
    }

    @Test
    void prepareForInterviewWhenScheduledSoon() throws Exception {
        String token = register("rec-interview@example.com");
        String applicationId = createApplication(token, "Interview Soon Co", "INTERVIEW");

        Instant soon = Instant.now().plus(36, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/applications/{id}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roundNumber": 1,
                                  "type": "TECHNICAL",
                                  "status": "SCHEDULED",
                                  "scheduledAt": "%s"
                                }
                                """.formatted(soon.toString())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications/{id}/recommendation", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("PREPARE_FOR_INTERVIEW"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void otherUserGets404ForRecommendation() throws Exception {
        String owner = register("rec-iso-owner@example.com");
        String other = register("rec-iso-other@example.com");
        String applicationId = createApplication(owner, "Private Co", "OFFER");

        mockMvc.perform(get("/api/applications/{id}/recommendation", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Application not found"));

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void freshSavedHasNoRecommendation() throws Exception {
        String token = register("rec-fresh@example.com");
        String applicationId = createApplication(token, "Fresh Co", "SAVED");

        mockMvc.perform(get("/api/applications/{id}/recommendation", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No recommendation available for this application"));
    }

    @Test
    void lowMatchScoreSuggestsImproveSkills() throws Exception {
        String token = register("rec-skills@example.com");
        String applicationId = createApplicationWithJd(token, """
                Java, Spring Boot, Kafka, PostgreSQL, Redis, Docker, Kubernetes, AWS
                """);

        mockMvc.perform(post("/api/applications/{id}/analyze", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(0)));

        mockMvc.perform(get("/api/applications/{id}/recommendation", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("IMPROVE_SKILLS"))
                .andExpect(jsonPath("$.priority").value("LOW"));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/applications/{id}/recommendation", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private void backdateUpdatedAt(String applicationId, Instant when) {
        jdbcTemplate.update(
                "UPDATE applications SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(when),
                UUID.fromString(applicationId)
        );
    }

    private String createApplication(String token, String company, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": "%s",
                                  "jobTitle": "Software Engineer",
                                  "status": "%s"
                                }
                                """.formatted(company, status)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createApplicationWithJd(String token, String jd) throws Exception {
        String escaped = objectMapper.writeValueAsString(jd);
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": "Skills Co",
                                  "jobTitle": "Backend Engineer",
                                  "status": "SAVED",
                                  "jobDescription": %s
                                }
                                """.formatted(escaped)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "name": "Rec User"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
