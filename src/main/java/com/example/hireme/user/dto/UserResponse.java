package com.example.hireme.user.dto;

import com.example.hireme.user.internal.User.Role;
import java.time.Instant;

public record UserResponse(
    Long id,
    String fullName,
    String email,
    String phoneNumber,
    Role role,
    Instant createdAt
) {}
