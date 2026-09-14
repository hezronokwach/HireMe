package com.example.hireme.user.dto;

import com.example.hireme.user.internal.User.Role;
import java.time.Instant;

public record AuthResponse(
    String token,
    Long userId,
    Role role,
    Instant expiresAt
) {}
