package com.example.hireme.user.internal;

import com.example.hireme.shared.security.JwtTokenProvider;
import com.example.hireme.user.AuthResponse;
import com.example.hireme.user.AuthService;
import com.example.hireme.user.DemoAccounts;
import com.example.hireme.user.DemoStatusResponse;
import com.example.hireme.user.LoginRequest;
import com.example.hireme.user.internal.exception.DemoUnavailableException;
import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // The demo owner can only be signed into when this deployment seeds demo data (a portfolio deployment).
    @Value("${hireme.demo-listings.enabled:false}")
    private boolean demoEnabled;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenFor(user);
    }

    @Override
    public DemoStatusResponse demoStatus() {
        boolean available = demoEnabled && userRepository.existsByEmail(DemoAccounts.OWNER_EMAIL);
        return new DemoStatusResponse(available, available ? DemoAccounts.OWNER_EMAIL : null);
    }

    @Override
    public AuthResponse demoOwnerLogin() {
        if (!demoEnabled) {
            throw new DemoUnavailableException();
        }
        return tokenFor(userRepository.findByEmail(DemoAccounts.OWNER_EMAIL)
                .orElseThrow(DemoUnavailableException::new));
    }

    private AuthResponse tokenFor(User user) {
        String role = user.getRole().name();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        String token = jwtTokenProvider.generateToken(user.getEmail(), role);
        Instant expiresAt = Instant.now().plusMillis(jwtTokenProvider.getJwtExpirationMs());

        return new AuthResponse(
                token,
                user.getId(),
                user.getRole(),
                expiresAt
        );
    }
}
