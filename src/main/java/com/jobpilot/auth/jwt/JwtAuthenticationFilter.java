package com.jobpilot.auth.jwt;

import com.jobpilot.auth.security.UserPrincipal;
import com.jobpilot.user.User;
import com.jobpilot.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    JwtService.ParsedToken parsed = jwtService.parse(token);
                    userRepository.findById(parsed.userId()).ifPresent(user -> {
                        if (user.isEnabled()) {
                            SecurityContextHolder.getContext().setAuthentication(toAuthentication(user));
                        }
                    });
                } catch (JwtService.InvalidJwtException ignored) {
                    // Leave context unauthenticated; SecurityFilterChain returns 401 where required.
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken toAuthentication(User user) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
