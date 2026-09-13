package com.jobpilot.jobanalysis;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class JobAnalysisFlowIntegrationTest {

    private static final String JD = """
            Looking for a Java / Spring Boot engineer with Kafka, Postgres, Redis,
            Docker, Kubernetes (k8s), and AWS. System Design interviews included.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void analyzeOwnedApplicationStoresRequirementsAndReturnsMatch() throws Exception {
        String token = register("ja-owner@example.com");
        createSkill(token, "Java");
        createSkill(token, "Kafka");
        createSkill(token, "Docker");
        createSkill(token, "Redis");
        createSkill(token, "Kubernetes");
        // missing: spring boot, postgresql, aws, system design → 5 matched of 9 = 56

        String applicationId = createApplication(token, JD);

        mockMvc.perform(post("/api/applications/{id}/analyze", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(56))
                .andExpect(jsonPath("$.matchedSkills", containsInAnyOrder(
                        "docker", "java", "kafka", "kubernetes", "redis")))
                .andExpect(jsonPath("$.missingSkills", containsInAnyOrder(
                        "aws", "postgresql", "spring boot", "system design")))
                .andExpect(jsonPath("$.requiredSkills", hasSize(9)));

        // Re-analyze replaces requirements; match endpoint recomputes from stored rows
        mockMvc.perform(get("/api/applications/{id}/match", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(56))
                .andExpect(jsonPath("$.requiredSkills", hasSize(9)));
    }

    @Test
    void cannotAnalyzeAnotherUsersApplication() throws Exception {
        String ownerToken = register("ja-iso-owner@example.com");
        String otherToken = register("ja-iso-other@example.com");
        String applicationId = createApplication(ownerToken, JD);

        mockMvc.perform(post("/api/applications/{id}/analyze", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Application not found"));

        mockMvc.perform(get("/api/applications/{id}/match", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankJobDescriptionReturns400() throws Exception {
        String token = register("ja-blank@example.com");
        String applicationId = createApplication(token, null);

        mockMvc.perform(post("/api/applications/{id}/analyze", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("jobDescription is blank"));
    }

    @Test
    void matchWithoutPriorAnalyzeReAnalyzesWhenJdPresent() throws Exception {
        String token = register("ja-match-first@example.com");
        createSkill(token, "Java");
        String applicationId = createApplication(token, "Need Java and Docker experience.");

        mockMvc.perform(get("/api/applications/{id}/match", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills", containsInAnyOrder("java", "docker")))
                .andExpect(jsonPath("$.matchedSkills", contains("java")))
                .andExpect(jsonPath("$.missingSkills", contains("docker")))
                .andExpect(jsonPath("$.score").value(50));
    }

    @Test
    void previewAnalyzesWithoutPersisting() throws Exception {
        String token = register("ja-preview@example.com");
        createSkill(token, "Python");

        mockMvc.perform(post("/api/job-analysis/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"Python and Redis required. Also K8s."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSkills", containsInAnyOrder("python", "redis", "kubernetes")))
                .andExpect(jsonPath("$.matchedSkills", contains("python")))
                .andExpect(jsonPath("$.score").value(33));
    }

    @Test
    void authRequired() throws Exception {
        mockMvc.perform(post("/api/applications/{id}/analyze", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications/{id}/match", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/job-analysis/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobDescription":"Java"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String createApplication(String token, String jobDescription) throws Exception {
        String body;
        if (jobDescription == null) {
            body = """
                    {"company":"Acme","jobTitle":"SWE"}
                    """;
        } else {
            String escaped = objectMapper.writeValueAsString(jobDescription);
            body = """
                    {"company":"Acme","jobTitle":"SWE","jobDescription":%s}
                    """.formatted(escaped);
        }
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"JA User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
