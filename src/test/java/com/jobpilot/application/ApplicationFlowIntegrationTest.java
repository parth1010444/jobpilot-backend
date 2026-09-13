package com.jobpilot.application;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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
class ApplicationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListFilterGetPatchDeleteAsOwner() throws Exception {
        String token = register("owner-apps@example.com", "password123", "Owner");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company":"Google",
                                  "jobTitle":"Software Engineer",
                                  "jobUrl":"https://careers.google.com/jobs/1",
                                  "location":"Mountain View",
                                  "employmentType":"FULL_TIME",
                                  "source":"LINKEDIN",
                                  "status":"SAVED",
                                  "salaryMin":150000,
                                  "salaryMax":200000,
                                  "notes":"Dream role"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.company").value("Google"))
                .andExpect(jsonPath("$.jobTitle").value("Software Engineer"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.userId").exists())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();
        long version = created.get("version").asLong();

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company":"Meta",
                                  "jobTitle":"Backend Engineer",
                                  "source":"REFERRAL",
                                  "status":"APPLIED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").exists());

        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "company,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("status", "SAVED")
                        .param("q", "google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].company").value("Google"));

        mockMvc.perform(get("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.company").value("Google"));

        MvcResult patchResult = mockMvc.perform(patch("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "status": "APPLIED",
                                  "notes": "Submitted online"
                                }
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.notes").value("Submitted online"))
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.appliedAt").exists())
                .andReturn();

        long newVersion = objectMapper.readTree(patchResult.getResponse().getContentAsString())
                .get("version").asLong();

        mockMvc.perform(delete("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        // ensure list no longer includes deleted (still has Meta)
        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].company").value("Meta"));

        // silence unused warning for newVersion in happy path — used conceptually
        org.junit.jupiter.api.Assertions.assertTrue(newVersion > version);
    }

    @Test
    void cannotAccessAnotherUsersApplication() throws Exception {
        String ownerToken = register("owner-iso@example.com", "password123", "Owner");
        String otherToken = register("other-iso@example.com", "password123", "Other");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Stripe","jobTitle":"Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"notes":"hack"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTransitionRejected() throws Exception {
        String token = register("transition@example.com", "password123", "T");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Acme","jobTitle":"Dev","status":"SAVED"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();
        long version = created.get("version").asLong();

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"status":"OFFER"}
                                """.formatted(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid status transition")));
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        String token = register("optlock@example.com", "password123", "O");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Netflix","jobTitle":"SWE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();
        long version = created.get("version").asLong();

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"notes":"first update"}
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"notes":"stale update"}
                                """.formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", not(blankOrNullString())));
    }

    @Test
    void authRequiredForApplications() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"X","jobTitle":"Y"}
                                """))
                .andExpect(status().isUnauthorized());

        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/applications/{id}", randomId))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email, String password, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","name":"%s"}
                                """.formatted(email, password, name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
