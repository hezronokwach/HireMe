package com.example.hireme;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The API docs are served without a token and describe every endpoint with the JWT scheme.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocsTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode spec() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), response.body());
        // Saved so the generated spec can be read or fed to a client generator after a build.
        Files.writeString(Path.of("target", "openapi.json"), response.body());
        return jsonMapper.readTree(response.body());
    }

    @Test
    void spec_isPublicAndListsEveryEndpoint() throws Exception {
        JsonNode paths = spec().get("paths");

        List<String> expected = List.of(
                "/api/v1/auth/register", "/api/v1/auth/login",
                "/api/v1/equipment", "/api/v1/equipment/{id}", "/api/v1/equipment/{equipmentId}/availability",
                "/api/v1/bookings", "/api/v1/bookings/{id}", "/api/v1/bookings/my-bookings",
                "/api/v1/bookings/for-my-equipment", "/api/v1/bookings/{id}/confirm",
                "/api/v1/bookings/{id}/cancel", "/api/v1/bookings/{id}/complete");
        List<String> missing = new ArrayList<>(expected.stream().filter(p -> !paths.has(p)).toList());
        assertTrue(missing.isEmpty(), "missing paths: " + missing);
    }

    @Test
    void spec_declaresJwtBearerScheme_andAuthEndpointsDontNeedIt() throws Exception {
        JsonNode spec = spec();

        JsonNode scheme = spec.at("/components/securitySchemes/bearerAuth");
        assertEquals("http", scheme.get("type").asString());
        assertEquals("bearer", scheme.get("scheme").asString());
        assertEquals("bearerAuth", spec.at("/security/0").propertyNames().iterator().next());
        for (String auth : List.of("login", "register")) {
            JsonNode security = spec.at("/paths/~1api~1v1~1auth~1" + auth + "/post/security");
            assertTrue(security.isArray() && security.isEmpty(), auth + " must override the global token requirement with []");
            assertTrue(spec.at("/paths/~1api~1v1~1auth~1" + auth + "/post/responses/401").isMissingNode()
                    || auth.equals("login"), "only login documents 401");
        }
    }

    @Test
    void spec_documentsRealSuccessCodesAndErrorBodies() throws Exception {
        JsonNode paths = spec().get("paths");

        assertTrue(paths.at("/~1api~1v1~1equipment/post/responses").has("201"));
        assertFalse(paths.at("/~1api~1v1~1equipment/post/responses").has("200"));
        assertTrue(paths.at("/~1api~1v1~1equipment~1{id}/delete/responses").has("204"));
        assertTrue(paths.at("/~1api~1v1~1bookings/post/responses").has("201"));

        JsonNode conflict = paths.at("/~1api~1v1~1bookings~1{id}~1confirm/put/responses/409");
        assertEquals("#/components/schemas/ErrorResponse", conflict.at("/content/application~1json/schema/$ref").asString());
        assertTrue(paths.at("/~1api~1v1~1bookings~1my-bookings/get/responses").has("401"));
        assertEquals("#/components/schemas/BookingResponse",
                paths.at("/~1api~1v1~1bookings~1{id}~1confirm/put/responses/200/content/application~1json/schema/$ref").asString());
    }

    @Test
    void spec_hasReadableUniqueOperationIds() throws Exception {
        List<String> ids = new ArrayList<>();
        spec().get("paths").forEach(path -> path.forEach(operation -> ids.add(operation.get("operationId").asString())));

        assertEquals(ids.size(), ids.stream().distinct().count(), "duplicate operationIds: " + ids);
        assertTrue(ids.stream().noneMatch(id -> id.matches(".*_\\d+")), "generated suffixes: " + ids);
    }

    @Test
    void spec_hidesTheAuthenticatedPrincipalParameter() throws Exception {
        JsonNode params = spec().at("/paths/~1api~1v1~1bookings~1my-bookings/get/parameters");

        List<String> names = new ArrayList<>();
        params.forEach(p -> names.add(p.get("name").asString()));
        assertEquals(List.of("page", "size", "sort"), names);
    }

    @Test
    void swaggerUi_isPublic() throws Exception {
        assertEquals(200, get("/swagger-ui/index.html").statusCode());
    }
}
