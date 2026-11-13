package com.example.hireme.user.internal;

import com.example.hireme.shared.security.JwtTokenProvider;
import com.example.hireme.user.AuthResponse;
import com.example.hireme.user.DemoAccounts;
import com.example.hireme.user.DemoStatusResponse;
import com.example.hireme.user.LoginRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.internal.exception.DemoUnavailableException;
import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest("john@test.com", "password123");
        User user = new User();
        user.setId(1L);
        user.setEmail("john@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.CLIENT);

        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT")).thenReturn("token123");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.login(request);

        assertEquals("token123", response.token());
        assertEquals(1L, response.userId());
        assertEquals(Role.CLIENT, response.role());
        assertNotNull(response.expiresAt());
    }

    @Test
    void login_userNotFound_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("unknown@test.com", "password123");
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("john@test.com", "wrongpassword");
        User user = new User();
        user.setEmail("john@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.CLIENT);

        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "encoded")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_ownerRole_prependsRolePrefix() {
        LoginRequest request = new LoginRequest("owner@test.com", "password123");
        User user = new User();
        user.setId(2L);
        user.setEmail("owner@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.OWNER);

        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded")).thenReturn(true);
        when(jwtTokenProvider.generateToken("owner@test.com", "ROLE_OWNER")).thenReturn("token456");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(3600000L);

        AuthResponse response = authService.login(request);

        assertEquals("token456", response.token());
        assertEquals(Role.OWNER, response.role());
    }

    // Demo owner (portfolio deployments)

    private User demoOwner() {
        User user = new User();
        user.setId(3L);
        user.setEmail(DemoAccounts.OWNER_EMAIL);
        user.setRole(Role.OWNER);
        return user;
    }

    @Test
    void demoOwnerLogin_demoOn_signsInWithoutAPassword() {
        ReflectionTestUtils.setField(authService, "demoEnabled", true);
        when(userRepository.findByEmail(DemoAccounts.OWNER_EMAIL)).thenReturn(Optional.of(demoOwner()));
        when(jwtTokenProvider.generateToken(DemoAccounts.OWNER_EMAIL, "ROLE_OWNER")).thenReturn("demo-token");
        when(jwtTokenProvider.getJwtExpirationMs()).thenReturn(3_600_000L);

        AuthResponse response = authService.demoOwnerLogin();

        assertEquals("demo-token", response.token());
        assertEquals(Role.OWNER, response.role());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void demoOwnerLogin_demoOff_isNotFound() {
        assertThrows(DemoUnavailableException.class, () -> authService.demoOwnerLogin());
        verifyNoInteractions(userRepository, jwtTokenProvider);
    }

    @Test
    void demoOwnerLogin_demoOnButNotSeededYet_isNotFound() {
        ReflectionTestUtils.setField(authService, "demoEnabled", true);
        when(userRepository.findByEmail(DemoAccounts.OWNER_EMAIL)).thenReturn(Optional.empty());

        assertThrows(DemoUnavailableException.class, () -> authService.demoOwnerLogin());
    }

    @Test
    void demoStatus_reportsTheOwnerOnlyWhenDemoIsOnAndSeeded() {
        assertEquals(new DemoStatusResponse(false, null), authService.demoStatus());

        ReflectionTestUtils.setField(authService, "demoEnabled", true);
        when(userRepository.existsByEmail(DemoAccounts.OWNER_EMAIL)).thenReturn(true);

        assertEquals(new DemoStatusResponse(true, DemoAccounts.OWNER_EMAIL), authService.demoStatus());
    }
}
