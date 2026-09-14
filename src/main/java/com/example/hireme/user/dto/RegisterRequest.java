package com.example.hireme.user.dto;

import com.example.hireme.user.internal.User.Role;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    String fullName,

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(?:\\+254|0)7\\d{8}$", message = "Invalid Kenyan phone number format")
    String phoneNumber,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password,

    @NotNull(message = "Role is required")
    Role role
) {}
