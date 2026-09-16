package com.example.hireme;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires a running PostgreSQL instance — run in integration-test profile only")
@SpringBootTest
class HireMeApplicationTests {

    @Test
    void contextLoads() {
    }
}
