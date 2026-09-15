package com.example.hireme.user.internal;

import com.example.hireme.shared.security.JwtTokenProvider;
import com.example.hireme.user.AuthService;
import com.example.hireme.user.dto.AuthResponse;
import com.example.hireme.user.dto.LoginRequest;
import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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
