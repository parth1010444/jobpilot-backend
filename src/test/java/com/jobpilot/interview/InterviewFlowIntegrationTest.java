package com.jobpilot.interview;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class InterviewFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListGetPatchDeleteAsOwner() throws Exception {
        String token = register("interview-owner@example.com");
        String applicationId = createApplication(token, "Interview Corp");

        MvcResult secondRoundResult = createInterview(
                token,
                applicationId,
                """
                        {
                          "roundNumber":2,
                          "type":"SYSTEM_DESIGN",
                          "scheduledAt":"2026-10-15T15:00:00Z",
                          "interviewer":"Grace Hopper",
                          "meetingLink":"https://meet.example/round-2",
                          "notes":"Discuss scalability"
                        }
                        """
        );
        JsonNode secondRound = objectMapper.readTree(secondRoundResult.getResponse().getContentAsString());
        String interviewId = secondRound.get("id").asText();
        long version = secondRound.get("version").asLong();

        createInterview(
                token,
                applicationId,
                """
                        {
                          "roundNumber":1,
                          "type":"TECHNICAL",
                          "status":"SCHEDULED",
                          "scheduledAt":"2026-10-10T15:00:00Z"
                        }
                        """
        );

        mockMvc.perform(get("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].roundNumber").value(1))
                .andExpect(jsonPath("$.content[1].roundNumber").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.type").value("SYSTEM_DESIGN"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(patch("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version":%d,
                                  "status":"COMPLETED",
                                  "feedback":"Strong system design and communication",
                                  "notes":"Completed on time"
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.feedback").value("Strong system design and communication"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(delete("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersParentOrInterview() throws Exception {
        String ownerToken = register("interview-isolation-owner@example.com");
        String otherToken = register("interview-isolation-other@example.com");
        String applicationId = createApplication(ownerToken, "Private Corp");
        MvcResult result = createInterview(
                ownerToken,
                applicationId,
                """
                        {"roundNumber":1,"type":"HR","scheduledAt":"2026-11-01T10:00:00Z"}
                        """
        );
        JsonNode interview = objectMapper.readTree(result.getResponse().getContentAsString());
        String interviewId = interview.get("id").asText();

        mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roundNumber":2,"type":"OTHER","scheduledAt":"2026-11-02T10:00:00Z"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/interviews/{id}", interviewId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateRoundNumberReturnsConflict() throws Exception {
        String token = register("interview-duplicate@example.com");
        String applicationId = createApplication(token, "Duplicate Corp");
        String request = """
                {"roundNumber":1,"type":"OA","scheduledAt":"2026-10-01T10:00:00Z"}
                """;
        createInterview(token, applicationId, request);

        mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("Round number")));
    }

    @Test
    void terminalInterviewStatusCannotTransition() throws Exception {
        String token = register("interview-transition@example.com");
        String applicationId = createApplication(token, "Transition Corp");
        MvcResult result = createInterview(
                token,
                applicationId,
                """
                        {
                          "roundNumber":1,
                          "type":"TECHNICAL",
                          "status":"COMPLETED",
                          "scheduledAt":"2026-09-01T10:00:00Z"
                        }
                        """
        );
        JsonNode interview = objectMapper.readTree(result.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/interviews/{id}", interview.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"status":"SCHEDULED"}
                                """.formatted(interview.get("version").asLong())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid interview status transition")));
    }

    @Test
    void validatesRequiredFieldsAndPositiveRound() throws Exception {
        String token = register("interview-validation@example.com");
        String applicationId = createApplication(token, "Validation Corp");

        mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roundNumber":0,"interviewer":"Nobody"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("roundNumber")))
                .andExpect(jsonPath("$.message", containsString("type")))
                .andExpect(jsonPath("$.message", containsString("scheduledAt")));
    }

    @Test
    void authenticationRequiredForAllInterviewRoutes() throws Exception {
        UUID applicationId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();

        mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications/{applicationId}/interviews", applicationId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/interviews/{id}", interviewId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/interviews/{id}", interviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/interviews/{id}", interviewId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingInterviewDoesNotChangeApplicationStatus() throws Exception {
        String token = register("interview-app-status@example.com");
        String applicationId = createApplication(token, "Status Corp");
        createInterview(
                token,
                applicationId,
                """
                        {"roundNumber":1,"type":"MANAGERIAL","scheduledAt":"2026-12-01T10:00:00Z"}
                        """
        );

        mockMvc.perform(get("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAVED"));
    }

    private MvcResult createInterview(String token, String applicationId, String body) throws Exception {
        return mockMvc.perform(post("/api/applications/{applicationId}/interviews", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andReturn();
    }

    private String createApplication(String token, String company) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"%s","jobTitle":"Engineer","status":"SAVED"}
                                """.formatted(company)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"Interviewer"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
