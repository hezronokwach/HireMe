package com.example.hireme.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

// Behaviour that depends on PostgreSQL itself, which the H2 tests can't check.
class PostgresSpecificIT extends PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsRan_includingThePostgresOnlyOne() {
        List<String> versions = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertEquals(List.of("1", "2", "3", "4", "5"), versions);
    }

    @Test
    void checkConstraintsRejectUnknownStatuses() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "insert into app_equipment.equipment (owner_id, name, status) values (1, 'x', 'HIRED')"));
    }

    /**
     * Confirming a booking locks the equipment row (SELECT ... FOR UPDATE), so confirmations for one
     * item run one at a time. Hold that row lock from a separate connection and check that a confirm
     * sent over HTTP waits for it, then finishes once the lock is released.
     */
    @Test
    void confirmWaitsForTheEquipmentRowLock() throws Exception {
        User owner = newUser("OWNER");
        User client = newUser("CLIENT");
        long equipmentId = createEquipment(owner, "IT Locked", "Nyeri IT-lock", 3000);
        long bookingId = book(client, equipmentId, 20, 21).body().get("bookingId").asLong();

        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "select id from app_equipment.equipment where id = ? for update")) {
                lock.setLong(1, equipmentId);
                lock.executeQuery();
            }

            CompletableFuture<Response> confirm = CompletableFuture.supplyAsync(
                    () -> put("/api/v1/bookings/" + bookingId + "/confirm", owner.token(), null));

            assertThrows(TimeoutException.class, () -> confirm.get(2, TimeUnit.SECONDS),
                    "confirm should wait while another transaction holds the equipment row");

            holder.commit(); // release the lock
            Response response = confirm.get(15, TimeUnit.SECONDS);
            assertEquals(200, response.status(), response.toString());
            assertEquals("CONFIRMED", response.body().get("status").asString());
        }
    }
}
