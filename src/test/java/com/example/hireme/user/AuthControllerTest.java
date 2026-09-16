package com.example.hireme.user;

import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import com.example.hireme.user.internal.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_validRequest_returnsCreated() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "+254712345678", "password123", Role.CLIENT);
        UserResponse response = new UserResponse(1L, "John", "john@test.com", "+254712345678", Role.CLIENT, Instant.now());

        when(userService.register(request)).thenReturn(response);

        ResponseEntity<UserResponse> result = authController.register(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("john@test.com", result.getBody().email());
    }

    @Test
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = new RegisterRequest("John", "john@test.com", "+254712345678", "password123", Role.CLIENT);
        when(userService.register(request)).thenThrow(new UserAlreadyExistsException("john@test.com"));

        assertThrows(UserAlreadyExistsException.class, () -> authController.register(request));
    }

    @Test
    void login_validCredentials_returnsOk() {
        LoginRequest request = new LoginRequest("john@test.com", "password123");
        AuthResponse response = new AuthResponse("token123", 1L, Role.CLIENT, Instant.now());

        when(authService.login(request)).thenReturn(response);

        ResponseEntity<AuthResponse> result = authController.login(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("token123", result.getBody().token());
    }

    @Test
    void login_invalidCredentials_throwsException() {
        LoginRequest request = new LoginRequest("john@test.com", "wrongpassword");
        when(authService.login(request)).thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class, () -> authController.login(request));
    }
}
