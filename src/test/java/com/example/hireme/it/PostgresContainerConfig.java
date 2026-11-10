package com.example.hireme.it;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A PostgreSQL 16 container (the same major version as docker-compose.yml) for integration tests.
 * {@code @ServiceConnection} points the datasource at it. Spring caches the test context, so all
 * integration tests share one container per run.
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
