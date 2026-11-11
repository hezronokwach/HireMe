package com.example.hireme.shared;

import com.example.hireme.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "mySecretKeyThatIsLongEnoughForHmacSha256Algorithm!");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
    }

    @Test
    void generateToken_returnsToken() {
        String token = jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractEmail_fromValidToken_returnsEmail() {
        String token = jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT");

        String email = jwtTokenProvider.extractEmail(token);

        assertEquals("john@test.com", email);
    }

    @Test
    void extractRole_fromValidToken_returnsRole() {
        String token = jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT");

        String role = jwtTokenProvider.extractRole(token);

        assertEquals("ROLE_CLIENT", role);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT");

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1L);
        String token = jwtTokenProvider.generateToken("john@test.com", "ROLE_CLIENT");

        assertFalse(jwtTokenProvider.validateToken(token));
    }

    @Test
    void getJwtExpirationMs_returnsValue() {
        assertEquals(3600000L, jwtTokenProvider.getJwtExpirationMs());
    }
}
