package com.example.hireme.user.internal;

import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Test
    void toEntity_mapsAllFields() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "+254712345678", "password123", Role.CLIENT);

        User user = userMapper.toEntity(request, "encoded");

        assertEquals("John", user.getFullName());
        assertEquals("john@test.com", user.getEmail());
        assertEquals("+254712345678", user.getPhoneNumber());
        assertEquals("encoded", user.getPasswordHash());
        assertEquals(Role.CLIENT, user.getRole());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void toResponse_mapsAllFields() {
        User user = new User();
        user.setId(1L);
        user.setFullName("John");
        user.setEmail("john@test.com");
        user.setPhoneNumber("+254712345678");
        user.setRole(Role.CLIENT);
        Instant now = Instant.now();
        user.setCreatedAt(now);

        UserResponse response = userMapper.toResponse(user);

        assertEquals(1L, response.id());
        assertEquals("John", response.fullName());
        assertEquals("john@test.com", response.email());
        assertEquals("+254712345678", response.phoneNumber());
        assertEquals(Role.CLIENT, response.role());
        assertEquals(now, response.createdAt());
    }
}
