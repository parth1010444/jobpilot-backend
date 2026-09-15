package com.jobpilot.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobpilot.auth.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

    private RateLimitProperties properties;
    private InMemoryRateLimitStore store;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setDefaultLimit(3);
        properties.setAuthLimit(2);
        properties.setWindow(Duration.ofMinutes(1));
        store = new InMemoryRateLimitStore(Clock.systemUTC());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(store, properties, mapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedAuthPathReturns429AfterLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = authRegister();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(authRegister(), limited, chain);

        assertThat(limited.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(limited.getHeader(RateLimitFilter.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getContentAsString()).contains("Rate limit exceeded");
        assertThat(limited.getContentAsString()).contains("\"status\":429");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(limited));
    }

    @Test
    void optionsPreflightIsExcluded() throws Exception {
        properties.setAuthLimit(0);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/register");
        request.setRequestURI("/api/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void pingIsExcluded() throws Exception {
        properties.setDefaultLimit(0);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.setRequestURI("/api/v1/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void authenticatedUserIsKeyedByUserId() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        authenticate(userId);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(meRequest("10.0.0.1"), response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(meRequest("10.0.0.1"), limited, chain);
        assertThat(limited.getStatus()).isEqualTo(429);

        SecurityContextHolder.clearContext();
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        authenticate(other);
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        filter.doFilter(meRequest("10.0.0.1"), otherResponse, chain);
        assertThat(otherResponse.getStatus()).isNotEqualTo(429);
    }

    @Test
    void xForwardedForIsPreferredOverRemoteAddr() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = authRegister();
            request.setRemoteAddr("9.9.9.9");
            request.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletRequest thirdSame = authRegister();
        thirdSame.setRemoteAddr("9.9.9.9");
        thirdSame.addHeader("X-Forwarded-For", "8.8.8.8, 1.1.1.1");
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(thirdSame, limited, chain);
        assertThat(limited.getStatus()).isEqualTo(429);

        MockHttpServletRequest otherIp = authRegister();
        otherIp.setRemoteAddr("9.9.9.9");
        otherIp.addHeader("X-Forwarded-For", "7.7.7.7");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(otherIp, ok, chain);
        assertThat(ok.getStatus()).isNotEqualTo(429);
    }

    @Test
    void clientIpPrefersForwardedThenRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.0.0.1");
        request.addHeader("X-Real-IP", "2.0.0.2");
        assertThat(RateLimitFilter.clientIp(request)).isEqualTo("2.0.0.2");

        request.addHeader("X-Forwarded-For", " 3.0.0.3 , 4.0.0.4");
        assertThat(RateLimitFilter.clientIp(request)).isEqualTo("3.0.0.3");
    }

    private static MockHttpServletRequest authRegister() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRequestURI("/api/auth/register");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static MockHttpServletRequest meRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setRequestURI("/api/users/me");
        request.setRemoteAddr(ip);
        return request;
    }

    private static void authenticate(UUID userId) {
        UserPrincipal principal = new UserPrincipal(userId, userId + "@example.com", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
