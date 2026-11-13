package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingResponse;
import com.example.hireme.booking.BookingService;
import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.CreateBookingRequest;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.user.DemoAccounts;
import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Booking requests on the demo listings, so the demo owner has something to confirm, decline and complete,
 * with the client's name and phone. They come from a demo client nobody can sign in as.
 * <p>
 * The dates are relative to today, and visitors act on them, so each start checks whether the demo client
 * still has an open booking (pending or confirmed, not yet over); if not, a fresh set is made.
 * Off unless {@code hireme.demo-listings.enabled=true}; runs after the demo listings are in place.
 */
@Component
@ConditionalOnProperty(name = "hireme.demo-listings.enabled", havingValue = "true")
class DemoBookingsSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoBookingsSeeder.class);

    /** One booking to make: on which demo listing, how many days from today, for how long, and whether to confirm it. */
    record Plan(String listing, int startsInDays, int days, boolean confirm) {
    }

    static final List<Plan> PLANS = List.of(
            new Plan("Massey Ferguson 375 Tractor", 5, 3, false),
            new Plan("Wedding Tent for 200 Guests", 12, 2, false),
            new Plan("PA Sound System with Microphones", 12, 1, false),
            new Plan("CAT 320 Excavator", 8, 4, true),
            new Plan("JCB 3CX Backhoe Loader", 2, 2, true)
    );

    private static final EnumSet<BookingStatus> OPEN = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final UserService userService;
    private final EquipmentService equipmentService;
    private final BookingService bookingService;
    private final Clock clock;

    DemoBookingsSeeder(UserService userService, EquipmentService equipmentService, BookingService bookingService,
                       Clock clock) {
        this.userService = userService;
        this.equipmentService = equipmentService;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Optional<Long> owner = userService.getUserIdByEmail(DemoAccounts.OWNER_EMAIL);
        if (owner.isEmpty()) {
            return;
        }
        Long ownerId = owner.get();
        Long clientId = userService.getUserIdByEmail(DemoAccounts.CLIENT_EMAIL).orElseGet(this::createClient);
        LocalDate today = LocalDate.now(clock);
        boolean hasOpen = bookingService.getMyBookings(clientId, PageRequest.of(0, 100)).content().stream()
                .anyMatch(b -> OPEN.contains(b.status()) && !b.endDate().isBefore(today));
        if (hasOpen) {
            log.info("Demo bookings: the demo client still has open bookings, nothing to add");
            return;
        }
        Map<String, Long> listingIds = equipmentService.getIdsByOwner(ownerId).stream()
                .map(equipmentService::getById)
                .collect(Collectors.toMap(EquipmentResponse::name, EquipmentResponse::id, (a, b) -> a));
        int made = 0;
        for (Plan plan : PLANS) {
            Long equipmentId = listingIds.get(plan.listing());
            if (equipmentId == null) {
                continue;
            }
            LocalDate start = today.plusDays(plan.startsInDays());
            try {
                BookingResponse booking = bookingService.createBooking(
                        new CreateBookingRequest(equipmentId, start, start.plusDays(plan.days() - 1L)), clientId);
                if (plan.confirm()) {
                    bookingService.confirm(booking.bookingId(), ownerId);
                }
                made++;
            } catch (RuntimeException e) {
                // e.g. a visitor's own confirmed booking already holds those dates: skip this one
                log.info("Demo bookings: skipped {} ({})", plan.listing(), e.getMessage());
            }
        }
        log.info("Demo bookings: {} added", made);
    }

    private Long createClient() {
        return userService.register(new RegisterRequest("Wanjiku Kamau", DemoAccounts.CLIENT_EMAIL,
                "+254711000000", UUID.randomUUID().toString(), Role.CLIENT)).id();
    }
}
