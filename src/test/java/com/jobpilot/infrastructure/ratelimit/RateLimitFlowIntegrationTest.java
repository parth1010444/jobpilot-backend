package com.jobpilot.infrastructure.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "jobpilot.redis.enabled=false",
        "jobpilot.rate-limit.enabled=true",
        "jobpilot.rate-limit.default-limit=3",
        "jobpilot.rate-limit.auth-limit=2",
        "jobpilot.rate-limit.window=1m"
})
class RateLimitFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thirdAuthRequestFromSameIpIs429() throws Exception {
        register(1, "10.1.0.1");
        register(2, "10.1.0.1");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.1.0.1")
                        .content("""
                                {"email":"rl-auth-3@example.com","password":"password123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void pingAndHealthStayUnlimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    @Test
    void authenticatedApiReturns429AfterUserLimit() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "10.2.0.8")
                        .content("""
                                {"email":"rl-user@example.com","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }

    private void register(int n, String ip) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", ip)
                        .content("""
                                {"email":"rl-auth-%s@example.com","password":"password123"}
                                """.formatted(n)))
                .andExpect(status().isCreated());
    }
}
