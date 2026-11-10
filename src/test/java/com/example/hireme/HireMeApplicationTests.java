package com.example.hireme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Starts the full context on H2: Flyway runs the migrations and Hibernate validates the
// entities against the resulting schema (ddl-auto=validate).
@SpringBootTest
class HireMeApplicationTests {

    @Test
    void contextLoads_migrationsMatchEntities() {
    }
}
