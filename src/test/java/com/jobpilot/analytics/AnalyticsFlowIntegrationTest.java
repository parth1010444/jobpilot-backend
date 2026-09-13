package com.jobpilot.analytics;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
class AnalyticsFlowIntegrationTest {

    private static final String JD = """
            Looking for a Java / Spring Boot engineer with Kafka and Docker.
            """;

    private final AtomicInteger roundCounter = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void summaryFunnelTimelineAndSkillsGapForOwner() throws Exception {
        String token = register("analytics-owner@example.com");
        createSkill(token, "Java");
        createSkill(token, "Docker");

        // Status mix: SAVED x1, APPLIED x2, OA x1, INTERVIEW x1, OFFER x1, REJECTED x1, WITHDRAWN x1
        createApplication(token, "Saved Co", "SAVED", null, null);
        Instant appliedRecent = Instant.now().minus(3, ChronoUnit.DAYS);
        String appliedId1 = createApplication(token, "Applied Co 1", "APPLIED", appliedRecent.toString(), JD);
        createApplication(token, "Applied Co 2", "APPLIED", appliedRecent.toString(), null);
        createApplication(token, "OA Co", "OA", appliedRecent.toString(), null);
        String interviewAppId = createApplication(token, "Interview Co", "INTERVIEW", appliedRecent.toString(), null);
        createApplication(token, "Offer Co", "OFFER", appliedRecent.toString(), null);
        createApplication(token, "Rejected Co", "REJECTED", appliedRecent.toString(), null);
        createApplication(token, "Withdrawn Co", "WITHDRAWN", null, null);

        createInterview(token, interviewAppId);
        createInterview(token, interviewAppId);

        mockMvc.perform(post("/api/applications/{id}/analyze", appliedId1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(50));

        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(8))
                .andExpect(jsonPath("$.interviewCount").value(2))
                .andExpect(jsonPath("$.offerCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.activeApplications").value(6))
                .andExpect(jsonPath("$.averageMatchScore", closeTo(50.0, 0.01)))
                .andExpect(jsonPath("$.countsByStatus", hasSize(7)));

        mockMvc.perform(get("/api/analytics/funnel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages", hasSize(5)))
                .andExpect(jsonPath("$.stages[0].status").value("SAVED"))
                .andExpect(jsonPath("$.stages[0].count").value(1))
                .andExpect(jsonPath("$.stages[0].conversionFromPrevious").value(nullValue()))
                .andExpect(jsonPath("$.stages[1].status").value("APPLIED"))
                .andExpect(jsonPath("$.stages[1].count").value(2))
                .andExpect(jsonPath("$.stages[1].conversionFromPrevious", closeTo(2.0, 0.01)))
                .andExpect(jsonPath("$.stages[2].status").value("OA"))
                .andExpect(jsonPath("$.stages[2].count").value(1))
                .andExpect(jsonPath("$.stages[2].conversionFromPrevious", closeTo(0.5, 0.01)))
                .andExpect(jsonPath("$.stages[3].status").value("INTERVIEW"))
                .andExpect(jsonPath("$.stages[3].count").value(1))
                .andExpect(jsonPath("$.stages[4].status").value("OFFER"))
                .andExpect(jsonPath("$.stages[4].count").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.withdrawnCount").value(1));

        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusWeeks(2);
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        MvcResult timelineResult = mockMvc.perform(get("/api/analytics/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("bucket", "DAY")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("DAY"))
                .andExpect(jsonPath("$.from").value(from.toString()))
                .andExpect(jsonPath("$.to").value(to.toString()))
                .andExpect(jsonPath("$.points").isArray())
                .andReturn();

        JsonNode points = objectMapper.readTree(timelineResult.getResponse().getContentAsString()).get("points");
        long created = 0;
        long applied = 0;
        for (JsonNode p : points) {
            created += p.get("applicationsCreated").asLong();
            applied += p.get("applicationsApplied").asLong();
        }
        org.assertj.core.api.Assertions.assertThat(created).isEqualTo(8);
        org.assertj.core.api.Assertions.assertThat(applied).isEqualTo(6);

        mockMvc.perform(get("/api/analytics/skills-gap")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingSkills").isArray())
                .andExpect(jsonPath("$.missingSkills", hasSize(2)))
                .andExpect(jsonPath("$.missingSkills[0].missingCount").value(1));
    }

    @Test
    void otherUsersDataNotVisible() throws Exception {
        String owner = register("analytics-iso-owner@example.com");
        String other = register("analytics-iso-other@example.com");

        Instant appliedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        String appId = createApplication(owner, "Owner Only", "OFFER", appliedAt.toString(), null);
        createInterview(owner, appId);

        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.interviewCount").value(0))
                .andExpect(jsonPath("$.offerCount").value(0))
                .andExpect(jsonPath("$.activeApplications").value(0))
                .andExpect(jsonPath("$.averageMatchScore").value(nullValue()));

        mockMvc.perform(get("/api/analytics/funnel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[4].count").value(0))
                .andExpect(jsonPath("$.rejectedCount").value(0));

        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.offerCount").value(1))
                .andExpect(jsonPath("$.interviewCount").value(1));
    }

    @Test
    void authRequiredForAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/funnel"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/timeline"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics/skills-gap"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTimelineRangeReturns400() throws Exception {
        String token = register("analytics-range@example.com");
        mockMvc.perform(get("/api/analytics/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must be on or before to"));
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"Analytics"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createApplication(
            String token,
            String company,
            String status,
            String appliedAt,
            String jobDescription
    ) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("company", company);
        node.put("jobTitle", "Engineer");
        node.put("status", status);
        if (appliedAt != null) {
            node.put("appliedAt", appliedAt);
        }
        if (jobDescription != null) {
            node.put("jobDescription", jobDescription);
        }
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void createInterview(String token, String applicationId) throws Exception {
        int round = roundCounter.getAndIncrement();
        mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roundNumber": %d,
                                  "type": "TECHNICAL",
                                  "scheduledAt": "2026-10-10T15:00:00Z"
                                }
                                """.formatted(round)))
                .andExpect(status().isCreated());
    }

    private void createSkill(String token, String name) throws Exception {
        mockMvc.perform(post("/api/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
