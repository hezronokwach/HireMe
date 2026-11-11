package com.example.hireme.user;

import com.example.hireme.user.internal.User;
import com.example.hireme.user.internal.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        User user = new User();
        user.setEmail("john@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.CLIENT);

        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("john@test.com");

        assertEquals("john@test.com", userDetails.getUsername());
        assertEquals("encoded", userDetails.getPassword());
        assertEquals(1, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT")));
    }

    @Test
    void loadUserByUsername_ownerRole_prependsRolePrefix() {
        User user = new User();
        user.setEmail("owner@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.OWNER);

        when(userRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("owner@test.com");

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER")));
    }

    @Test
    void loadUserByUsername_adminRole_prependsRolePrefix() {
        User user = new User();
        user.setEmail("admin@test.com");
        user.setPasswordHash("encoded");
        user.setRole(Role.ADMIN);

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin@test.com");

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_userNotFound_throwsException() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("unknown@test.com"));
    }
}
