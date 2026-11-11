package com.example.hireme.user;

import java.time.Instant;

public record UserResponse(
    Long id,
    String fullName,
    String email,
    String phoneNumber,
    Role role,
    Instant createdAt
) {}
