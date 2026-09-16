package com.example.hireme.user.internal;

import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserResponse;
import com.example.hireme.user.internal.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void register_newUser_returnsResponse() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "+254712345678", "password123", Role.CLIENT);
        User user = new User();
        user.setId(1L);
        user.setRole(Role.CLIENT);
        user.setCreatedAt(Instant.now());
        UserResponse response = new UserResponse(1L, "John", "john@test.com", "+254712345678", Role.CLIENT, Instant.now());

        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userMapper.toEntity(request, "encoded")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.register(request);

        assertEquals("john@test.com", result.email());
        verify(userRepository).save(user);
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "+254712345678", "password123", Role.CLIENT);
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.register(request));
    }

    @Test
    void isOwner_ownerRole_returnsTrue() {
        User user = new User();
        user.setRole(Role.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertTrue(userService.isOwner(1L));
    }

    @Test
    void isOwner_adminRole_returnsTrue() {
        User user = new User();
        user.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertTrue(userService.isOwner(1L));
    }

    @Test
    void isOwner_clientRole_returnsFalse() {
        User user = new User();
        user.setRole(Role.CLIENT);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertFalse(userService.isOwner(1L));
    }

    @Test
    void isOwner_userNotFound_returnsFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertFalse(userService.isOwner(1L));
    }

    @Test
    void getProfile_existingUser_returnsResponse() {
        User user = new User();
        user.setId(1L);
        UserResponse response = new UserResponse(1L, "John", "john@test.com", "+254712345678", Role.CLIENT, Instant.now());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.getProfile(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
    }

    @Test
    void getProfile_userNotFound_returnsNull() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertNull(userService.getProfile(1L));
    }

    @Test
    void getUserIdByEmail_existingUser_returnsId() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        Optional<Long> result = userService.getUserIdByEmail("john@test.com");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get());
    }

    @Test
    void getUserIdByEmail_userNotFound_returnsEmpty() {
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.empty());

        Optional<Long> result = userService.getUserIdByEmail("john@test.com");

        assertFalse(result.isPresent());
    }
}
