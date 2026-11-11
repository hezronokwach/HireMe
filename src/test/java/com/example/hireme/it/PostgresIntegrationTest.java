package com.example.hireme.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base for integration tests: the whole app on a random port, backed by PostgreSQL in Docker, called
 * over HTTP with real JWTs. Overrides the H2 settings from src/test/resources/application.properties
 * and runs the PostgreSQL-only migrations too.
 * <p>
 * Every test registers its own users (unique emails and phone numbers), because the database is
 * shared by all integration tests in a run and is not rolled back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/{vendor}",
        "spring.jpa.show-sql=false"
})
@Import(PostgresContainerConfig.class)
abstract class PostgresIntegrationTest {

    private static final AtomicInteger PHONE_SUFFIX = new AtomicInteger(10_000_000);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    protected record Response(int status, JsonNode body) {
    }

    protected record User(long id, String email, String token) {
    }

    // Registers a user with a unique email and phone number and logs them in.
    protected User newUser(String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        String phone = "07" + PHONE_SUFFIX.incrementAndGet();
        Response registered = post("/api/v1/auth/register", null, """
                {"fullName":"IT %s","email":"%s","phoneNumber":"%s","password":"secret123","role":"%s"}"""
                .formatted(role, email, phone, role));
        if (registered.status() != 201) {
            throw new IllegalStateException("register failed: " + registered);
        }
        Response login = post("/api/v1/auth/login", null, """
                {"email":"%s","password":"secret123"}""".formatted(email));
        return new User(login.body().get("userId").asLong(), email, login.body().get("token").asString());
    }

    protected long createEquipment(User owner, String name, String location, int dailyRate) {
        Response created = post("/api/v1/equipment", owner.token(), """
                {"name":"%s","category":"AGRICULTURAL","description":"IT","dailyRateKes":%d,"location":"%s"}"""
                .formatted(name, dailyRate, location));
        if (created.status() != 201) {
            throw new IllegalStateException("create equipment failed: " + created);
        }
        return created.body().get("id").asLong();
    }

    protected Response book(User client, long equipmentId, int startInDays, int endInDays) {
        return post("/api/v1/bookings", client.token(), """
                {"equipmentId":%d,"startDate":"%s","endDate":"%s"}"""
                .formatted(equipmentId, LocalDate.now().plusDays(startInDays), LocalDate.now().plusDays(endInDays)));
    }

    protected Response get(String path, String token) {
        return send(request(path, token).GET());
    }

    protected Response post(String path, String token, String json) {
        return send(request(path, token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    protected Response put(String path, String token, String json) {
        return send(request(path, token).header("Content-Type", "application/json")
                .PUT(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json)));
    }

    protected Response delete(String path, String token) {
        return send(request(path, token).DELETE());
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? null : jsonMapper.readTree(response.body());
            return new Response(response.statusCode(), body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
