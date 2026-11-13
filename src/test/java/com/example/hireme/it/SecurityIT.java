package com.example.hireme.it;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Authentication and authorisation errors over HTTP, on PostgreSQL.
class SecurityIT extends PostgresIntegrationTest {

    @Test
    void noOrBadToken_returns401() {
        assertEquals(401, get("/api/v1/equipment/mine", null).status());
        assertEquals(401, get("/api/v1/equipment/mine", "not-a-jwt").status());
    }

    @Test
    void browsing_needsNoToken() {
        assertEquals(200, get("/api/v1/equipment", null).status());
        assertEquals(404, get("/api/v1/equipment/999999", null).status());
        assertEquals(200, get("/api/v1/ai/status", null).status());
    }

    @Test
    void wrongPassword_returns401() {
        User client = newUser("CLIENT");

        Response login = post("/api/v1/auth/login", null, """
                {"email":"%s","password":"wrong-password"}""".formatted(client.email()));

        assertEquals(401, login.status());
    }

    @Test
    void wrongRole_returns403() {
        User client = newUser("CLIENT");

        Response create = post("/api/v1/equipment", client.token(), """
                {"name":"Nope","category":"AGRICULTURAL","description":"d","dailyRateKes":1000,"location":"Nairobi"}""");

        assertEquals(403, create.status());
    }

    @Test
    void someoneElsesListing_returns403() {
        User owner = newUser("OWNER");
        User otherOwner = newUser("OWNER");
        long equipmentId = createEquipment(owner, "IT Mine", "Kisumu IT-sec", 1000);

        assertEquals(403, put("/api/v1/equipment/" + equipmentId, otherOwner.token(), "{\"name\":\"Taken\"}").status());
        assertEquals(403, delete("/api/v1/equipment/" + equipmentId, otherOwner.token()).status());
    }

    @Test
    void duplicateEmail_returns409_butDuplicateNameIsFine() {
        User first = newUser("CLIENT");

        Response sameEmail = post("/api/v1/auth/register", null, """
                {"fullName":"Someone","email":"%s","phoneNumber":"0799999901","password":"secret123","role":"CLIENT"}"""
                .formatted(first.email()));
        assertEquals(409, sameEmail.status());

        // newUser always uses the full name "IT CLIENT", so this is a second user with the same name.
        assertDoesNotThrow(() -> newUser("CLIENT"));
    }
}
