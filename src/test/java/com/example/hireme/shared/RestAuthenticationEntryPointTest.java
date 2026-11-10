package com.example.hireme.shared;

import com.example.hireme.shared.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class RestAuthenticationEntryPointTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(jsonMapper);

    @Test
    void commence_returnsUnauthorizedWithBearerChallengeAndErrorBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        assertEquals("application/json", response.getContentType());
        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertEquals(401, body.get("status").asInt());
        assertEquals("Authentication required: missing, invalid or expired token", body.get("message").asString());
        assertTrue(body.hasNonNull("timestamp"));
    }
}
