package com.example.hireme.it;

import com.example.hireme.booking.internal.BookingActivator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// The main booking flow end to end, over HTTP, on PostgreSQL.
class BookingFlowIT extends PostgresIntegrationTest {

    @Autowired
    private BookingActivator bookingActivator; // the hourly job's logic, run on demand

    private static List<Long> ids(JsonNode page, String field) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(item -> ids.add(item.get(field).asLong()));
        return ids;
    }

    @Test
    void registerListBookConfirmComplete() {
        User owner = newUser("OWNER");
        User client = newUser("CLIENT");
        User otherClient = newUser("CLIENT");

        // Owner lists a tractor; clients can find it with the filters.
        long tractor = createEquipment(owner, "IT Tractor", "Nakuru IT-flow", 5000);
        Response search = get("/api/v1/equipment?location=nakuru%20it-flow&maxRate=6000", client.token());
        assertEquals(200, search.status());
        assertEquals(List.of(tractor), ids(search.body(), "id"));

        // Two clients ask for overlapping dates: both requests are accepted while PENDING.
        Response first = book(client, tractor, 10, 12);
        Response second = book(otherClient, tractor, 12, 14);
        assertEquals(201, first.status(), first.toString());
        assertEquals(201, second.status(), second.toString());
        assertEquals("PENDING", first.body().get("status").asString());
        assertEquals(15000, first.body().get("totalCostKes").asInt(), "3 days at 5000, both ends counted");
        long firstId = first.body().get("bookingId").asLong();
        long secondId = second.body().get("bookingId").asLong();

        // The owner sees both requests waiting.
        Response waiting = get("/api/v1/bookings/for-my-equipment?status=PENDING&sort=startDate", owner.token());
        assertEquals(List.of(firstId, secondId), ids(waiting.body(), "bookingId"));

        // Confirming the first cancels the clashing second one.
        Response confirmed = put("/api/v1/bookings/" + firstId + "/confirm", owner.token(), null);
        assertEquals(200, confirmed.status(), confirmed.toString());
        assertEquals("CONFIRMED", confirmed.body().get("status").asString());
        assertEquals("CANCELLED", get("/api/v1/bookings/" + secondId, otherClient.token()).body().get("status").asString());

        // The confirmed dates are now booked: shown in availability and refused for new bookings.
        Response availability = get("/api/v1/equipment/" + tractor + "/availability?from=" + LocalDate.now()
                + "&to=" + LocalDate.now().plusDays(30), otherClient.token());
        assertFalse(availability.body().get("available").asBoolean());
        assertEquals(LocalDate.now().plusDays(10).toString(), availability.body().at("/bookedPeriods/0/startDate").asString());
        assertEquals(409, book(otherClient, tractor, 12, 12).status(), "last day of the confirmed booking");
        assertEquals(201, book(otherClient, tractor, 13, 13).status(), "the day after is free");

        // The equipment search's date filter agrees: booked on days 10-12, free from day 13.
        String nakuru = "/api/v1/equipment?location=nakuru%20it-flow";
        assertEquals(List.of(), ids(get(nakuru + "&availableFrom=" + LocalDate.now().plusDays(11)
                + "&availableTo=" + LocalDate.now().plusDays(12), otherClient.token()).body(), "id"));
        assertEquals(List.of(tractor), ids(get(nakuru + "&availableFrom=" + LocalDate.now().plusDays(20),
                otherClient.token()).body(), "id"));

        // The listing can't be deleted while it has open bookings.
        assertEquals(409, delete("/api/v1/equipment/" + tractor, owner.token()).status());

        // The owner completes the hire; the client sees it in their bookings.
        Response completed = put("/api/v1/bookings/" + firstId + "/complete", owner.token(), null);
        assertEquals("COMPLETED", completed.body().get("status").asString());
        Response mine = get("/api/v1/bookings/my-bookings", client.token());
        assertEquals(200, mine.status());
        assertEquals(1, mine.body().get("totalElements").asInt());
        assertEquals("COMPLETED", mine.body().at("/content/0/status").asString());
    }

    @Test
    void clientCannotCancelAnActiveBookingButOwnerCan() {
        User owner = newUser("OWNER");
        User client = newUser("CLIENT");
        long plough = createEquipment(owner, "IT Plough", "Eldoret IT-active", 2000);

        // Starts today, so confirming it and running activation makes it ACTIVE.
        long bookingId = book(client, plough, 0, 2).body().get("bookingId").asLong();
        put("/api/v1/bookings/" + bookingId + "/confirm", owner.token(), null);
        bookingActivator.activateDueBookings();
        assertEquals("ACTIVE", get("/api/v1/bookings/" + bookingId, client.token()).body().get("status").asString());

        assertEquals(403, put("/api/v1/bookings/" + bookingId + "/cancel", client.token(), null).status());
        assertEquals("CANCELLED", put("/api/v1/bookings/" + bookingId + "/cancel", owner.token(), null).body().get("status").asString());
    }
}
