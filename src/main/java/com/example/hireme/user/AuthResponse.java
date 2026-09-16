package com.example.hireme.user;

import java.time.Instant;

public record AuthResponse(
    String token,
    Long userId,
    Role role,
    Instant expiresAt
) {}
