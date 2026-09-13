package com.jobpilot.resume;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class ResumeFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createListGetPatchDeleteAsOwner() throws Exception {
        String token = register("resume-owner@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Backend SWE",
                                  "versionLabel":"V2",
                                  "description":"Tailored for backend roles",
                                  "fileUrl":"https://files.example/resume-v2.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Backend SWE"))
                .andExpect(jsonPath("$.versionLabel").value("V2"))
                .andExpect(jsonPath("$.description").value("Tailored for backend roles"))
                .andExpect(jsonPath("$.fileUrl").value("https://files.example/resume-v2.pdf"))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();

        mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"General"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Backend SWE"));

        mockMvc.perform(patch("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Backend SWE (updated)",
                                  "versionLabel":"V3",
                                  "description":"Added recent role"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Backend SWE (updated)"))
                .andExpect(jsonPath("$.versionLabel").value("V3"))
                .andExpect(jsonPath("$.description").value("Added recent role"))
                .andExpect(jsonPath("$.fileUrl").value("https://files.example/resume-v2.pdf"));

        mockMvc.perform(delete("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotAccessAnotherUsersResume() throws Exception {
        String ownerToken = register("resume-iso-owner@example.com");
        String otherToken = register("resume-iso-other@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Private resume","versionLabel":"V1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"hack"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/resumes/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAndUpdateApplicationWithOwnResume() throws Exception {
        String token = register("resume-app-owner@example.com");
        String resumeId = createResume(token, "Targeted");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Google","jobTitle":"SWE","resumeId":"%s"}
                                """.formatted(resumeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(resumeId))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String applicationId = created.get("id").asText();
        long version = created.get("version").asLong();

        String otherResumeId = createResume(token, "General");
        mockMvc.perform(patch("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"resumeId":"%s"}
                                """.formatted(version, otherResumeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(otherResumeId));
    }

    @Test
    void otherUsersResumeRejectedOnCreateAndUpdate() throws Exception {
        String ownerToken = register("resume-foreign-owner@example.com");
        String otherToken = register("resume-foreign-other@example.com");
        String ownerResumeId = createResume(ownerToken, "Owner resume");

        mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Stripe","jobTitle":"Engineer","resumeId":"%s"}
                                """.formatted(ownerResumeId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found"));

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Stripe","jobTitle":"Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/applications/{id}", created.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"resumeId":"%s"}
                                """.formatted(created.get("version").asLong(), ownerResumeId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found"));
    }

    @Test
    void patchApplicationCanUnlinkResumeWithExplicitNull() throws Exception {
        String token = register("resume-unlink@patch.example.com");
        String resumeId = createResume(token, "Linked");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Amazon","jobTitle":"SWE","resumeId":"%s"}
                                """.formatted(resumeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(resumeId))
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());

        mockMvc.perform(patch("/api/applications/{id}", created.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"resumeId":null}
                                """.formatted(created.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(nullValue()));
    }

    @Test
    void deletingResumeNullsApplicationResumeId() throws Exception {
        String token = register("resume-delete-set-null@example.com");
        String resumeId = createResume(token, "Will delete");

        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"Netflix","jobTitle":"SWE","resumeId":"%s"}
                                """.formatted(resumeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(resumeId))
                .andReturn();
        String applicationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(delete("/api/resumes/{id}", resumeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId))
                .andExpect(jsonPath("$.company").value("Netflix"))
                .andExpect(jsonPath("$.resumeId").value(nullValue()));
    }

    @Test
    void authRequiredForResumes() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X"}
                                """))
                .andExpect(status().isUnauthorized());

        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/resumes/{id}", randomId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/resumes/{id}", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Y"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/resumes/{id}", randomId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createResumeRequiresName() throws Exception {
        String token = register("resume-validation@example.com");
        mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"missing name"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String createResume(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","versionLabel":"V1"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","name":"Resume User"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
