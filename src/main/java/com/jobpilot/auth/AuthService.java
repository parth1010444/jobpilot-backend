package com.jobpilot.auth;

import com.jobpilot.auth.dto.AuthResponse;
import com.jobpilot.auth.dto.LoginRequest;
import com.jobpilot.auth.dto.RegisterRequest;
import com.jobpilot.auth.dto.UserResponse;
import com.jobpilot.auth.jwt.JwtService;
import com.jobpilot.common.error.JobPilotException;
import com.jobpilot.user.User;
import com.jobpilot.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new JobPilotException(HttpStatus.CONFLICT, "Email already registered");
        }

        String displayName = blankToNull(request.name());
        User user = new User(email, passwordEncoder.encode(request.password()), displayName);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(this::badCredentials);

        if (!user.isEnabled() || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw badCredentials();
        }

        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String token = jwtService.createAccessToken(user.getId(), user.getEmail());
        return AuthResponse.bearer(token, jwtService.getExpirationMs(), UserResponse.from(user));
    }

    private JobPilotException badCredentials() {
        return new JobPilotException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
