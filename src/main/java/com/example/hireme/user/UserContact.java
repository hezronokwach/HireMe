package com.example.hireme.user;

/** How to reach a user: what other modules may show about them (no email, role or password). */
public record UserContact(
        Long id,
        String fullName,
        String phoneNumber
) {
}
