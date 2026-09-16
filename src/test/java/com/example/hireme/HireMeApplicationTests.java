package com.example.hireme;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context smoke test.
 * <p>
 * Disabled in the standard test phase because it requires a running PostgreSQL
 * instance. Run this manually or in a dedicated integration-test profile
 * (e.g. {@code mvn verify -Pintegration-tests}) once a DB is available.
 */
@Disabled("Requires a running PostgreSQL instance — run in integration-test profile only")
@SpringBootTest
class HireMeApplicationTests {

    @Test
    void contextLoads() {
        // Full-stack smoke test — verifies the complete Spring context starts.
    }

}

