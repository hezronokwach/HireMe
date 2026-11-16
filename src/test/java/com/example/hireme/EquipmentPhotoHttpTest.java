package com.example.hireme;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Uploads, serves and deletes a real photo through the whole stack: security rules, multipart, the database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EquipmentPhotoHttpTest {

    // A 1×1 PNG.
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void ownerAddsAPhoto_anyoneSeesIt_onlyTheOwnerRemovesIt() throws Exception {
        String owner = signUp("OWNER");
        long listing = jsonMapper.readTree(send(json("POST", "/api/v1/equipment", owner, """
                {"name":"Photo Tractor","category":"AGRICULTURAL","description":"d","dailyRateKes":5000,"location":"Nakuru"}"""))
                .body()).get("id").asLong();

        HttpResponse<String> upload = send(multipart("/api/v1/equipment/" + listing + "/photos", owner, PNG));
        assertEquals(201, upload.statusCode(), upload.body());
        String url = jsonMapper.readTree(upload.body()).get("url").asString();

        // No token needed to see it, and browsers may keep it.
        HttpResponse<byte[]> image = http.send(request(url, null).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(PNG, image.body());
        assertTrue(image.headers().firstValue("Cache-Control").orElse("").contains("max-age"));

        // The listing now carries the photo, for the list and the detail page.
        JsonNode detail = jsonMapper.readTree(send(request("/api/v1/equipment/" + listing, null).GET().build()).body());
        assertEquals(url, detail.get("photoUrls").get(0).asString());

        // A client can't add or remove photos.
        String client = signUp("CLIENT");
        assertEquals(403, send(multipart("/api/v1/equipment/" + listing + "/photos", client, PNG)).statusCode());
        assertEquals(403, send(request(url, client).DELETE().build()).statusCode());
        // Nor can a signed-out visitor.
        assertEquals(401, send(multipart("/api/v1/equipment/" + listing + "/photos", null, PNG)).statusCode());

        // Something that isn't an image is refused, whatever it calls itself.
        HttpResponse<String> fake = send(multipart("/api/v1/equipment/" + listing + "/photos", owner,
                "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(400, fake.statusCode());

        assertEquals(204, send(request(url, owner).DELETE().build()).statusCode());
        assertEquals(404, send(request(url, null).GET().build()).statusCode());
    }

    private String signUp(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> registered = send(json("POST", "/api/v1/auth/register", null, """
                {"fullName":"Photo Test %s","email":"%s","phoneNumber":"%s","password":"secret123","role":"%s"}"""
                .formatted(UUID.randomUUID(), email, randomPhone(), role)));
        assertEquals(201, registered.statusCode(), registered.body());
        HttpResponse<String> login = send(json("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"secret123\"}"));
        return jsonMapper.readTree(login.body()).get("token").asString();
    }

    // Phone numbers are unique, and the test database is shared between tests.
    private static String randomPhone() {
        return "07" + String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private HttpRequest json(String method, String path, String token, String body) {
        return request(path, token).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private HttpRequest multipart(String path, String token, byte[] file) throws Exception {
        String boundary = "----hireme" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request(path, token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
