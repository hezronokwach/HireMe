package com.example.hireme;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

// Calls the running app over HTTP so the whole security filter chain is exercised:
// requests without a valid token get 401, logged-in users without the role get 403.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityErrorResponsesTest {

    private static final String PROTECTED_URL = "/api/v1/equipment/mine";
    private static final String OWNER_ONLY_POST = "/api/v1/equipment";

    @Value("${local.server.port}")
    private int port;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void registerClient() throws Exception {
        // Ignore the 409 when another test already registered this user in the shared H2 database.
        send(post("/api/v1/auth/register", null, """
                {"fullName":"Security Test","email":"security-test@example.com","phoneNumber":"0700000001",
                 "password":"secret123","role":"CLIENT"}"""));
    }

    @Test
    void noToken_returns401WithBearerChallenge() throws Exception {
        HttpResponse<String> response = send(get(PROTECTED_URL, null));

        assertEquals(401, response.statusCode());
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null));
        assertEquals(401, jsonMapper.readTree(response.body()).get("status").asInt());
    }

    @Test
    void malformedToken_returns401() throws Exception {
        assertEquals(401, send(get(PROTECTED_URL, "not-a-jwt")).statusCode());
    }

    @Test
    void expiredToken_returns401() throws Exception {
        String expired = Jwts.builder()
                .subject("security-test@example.com")
                .claim("role", "CLIENT")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertEquals(401, send(get(PROTECTED_URL, expired)).statusCode());
    }

    @Test
    void validTokenWithoutRequiredRole_returns403() throws Exception {
        String clientToken = login();

        HttpResponse<String> response = send(post(OWNER_ONLY_POST, clientToken, """
                {"name":"Tractor","category":"AGRICULTURAL","description":"d","dailyRateKes":5000,"location":"Nakuru"}"""));

        assertEquals(403, response.statusCode());
    }

    @Test
    void validTokenWithoutTheRoleForAnOwnerPage_returns403() throws Exception {
        assertEquals(403, send(get(PROTECTED_URL, login())).statusCode());
    }

    // Browsing is public: signing in is only needed to book or to list equipment.

    @Test
    void browsingWithoutAToken_isAllowed() throws Exception {
        assertEquals(200, send(get("/api/v1/equipment", null)).statusCode());
        assertEquals(404, send(get("/api/v1/equipment/999999", null)).statusCode());
        assertEquals(200, send(get("/api/v1/ai/status", null)).statusCode());
        assertEquals(200, send(get("/api/v1/auth/demo", null)).statusCode());
    }

    @Test
    void ownerAndBookingEndpoints_stillNeedAToken() throws Exception {
        assertEquals(401, send(get("/api/v1/bookings/my-bookings", null)).statusCode());
        assertEquals(401, send(get("/api/v1/bookings/for-my-equipment", null)).statusCode());
        assertEquals(401, send(post("/api/v1/bookings", null, "{}")).statusCode());
    }

    @Test
    void demoOwnerSignIn_isNotFoundWhenTheServerHasNoDemoData() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/auth/demo-owner", null, ""));

        assertEquals(404, response.statusCode());
        assertFalse(jsonMapper.readTree(send(get("/api/v1/auth/demo", null)).body()).get("ownerAvailable").asBoolean());
    }

    private String login() throws Exception {
        HttpResponse<String> response = send(post("/api/v1/auth/login", null,
                "{\"email\":\"security-test@example.com\",\"password\":\"secret123\"}"));
        assertEquals(200, response.statusCode(), response.body());
        return jsonMapper.readTree(response.body()).get("token").asString();
    }

    private HttpRequest get(String path, String token) {
        return request(path, token).GET().build();
    }

    private HttpRequest post(String path, String token, String json) {
        return request(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
