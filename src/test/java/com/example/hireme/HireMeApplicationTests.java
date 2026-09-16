package com.example.hireme;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test verifying the Spring application context starts successfully.
 * DataSource, JPA, and transaction manager auto-configurations are excluded
 * so this test runs in CI without a real PostgreSQL instance.
 */
@SpringBootTest(
        classes = HireMeApplication.class,
        properties = "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
)
class HireMeApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context loads without a database connection.
    }

}
