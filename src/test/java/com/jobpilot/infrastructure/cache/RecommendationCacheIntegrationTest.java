package com.jobpilot.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.application.ApplicationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private ApplicationRepository applicationRepository;

    @Test
    void recommendationsAreCachedUntilAWriteEvictsThem() throws Exception {
        String token = register("cache-owner@example.com");
        UUID userId = currentUserId(token);

        String applicationId = createApplication(token, "Stale Co", "APPLIED");
        backdateUpdatedAt(applicationId, Instant.now().minus(10, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("FOLLOW_UP"));

        Cache cache = cacheManager.getCache(CacheNames.RECOMMENDATIONS);
        assertThat(cache).isNotNull();
        assertThat(cache.get(CacheNames.recommendationsKey(userId, 10))).isNotNull();

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("FOLLOW_UP"));

        verify(applicationRepository, times(1)).findByUserId(userId);

        // Bypass service (no eviction): cached response stays stale
        jdbcTemplate.update(
                "UPDATE applications SET status = 'OFFER' WHERE id = ?",
                UUID.fromString(applicationId)
        );

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("FOLLOW_UP"));

        // Status change through the API evicts recommendations
        int version = jdbcTemplate.queryForObject(
                "SELECT version FROM applications WHERE id = ?",
                Integer.class,
                UUID.fromString(applicationId)
        );
        mockMvc.perform(patch("/api/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %s, "status": "OFFER"}
                                """.formatted(version)))
                .andExpect(status().isOk());

        assertThat(cache.get(CacheNames.recommendationsKey(userId, 10))).isNull();

        mockMvc.perform(get("/api/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("EVALUATE_OFFER"));
    }

    @Test
    void skillChangeEvictsApplicationMatchCache() throws Exception {
        String token = register("cache-match@example.com");
        UUID userId = currentUserId(token);
        String applicationId = createApplicationWithJd(token, "Java, Kafka, Redis");

        mockMvc.perform(post("/api/applications/{id}/analyze", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));

        mockMvc.perform(get("/api/applications/{id}/match", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));

        Cache cache = cacheManager.getCache(CacheNames.APPLICATION_MATCH);
        assertThat(cache).isNotNull();
        assertThat(cache.get(CacheNames.applicationMatchKey(userId, UUID.fromString(applicationId)))).isNotNull();

        mockMvc.perform(post("/api/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Java"}
                                """))
                .andExpect(status().isCreated());

        assertThat(cache.get(CacheNames.applicationMatchKey(userId, UUID.fromString(applicationId)))).isNull();

        mockMvc.perform(get("/api/applications/{id}/match", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(33));
    }

    private UUID currentUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
                                  "company": "Match Co",
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
                                  "name": "Cache User"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
