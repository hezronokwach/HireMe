package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingResponse;
import com.example.hireme.booking.BookingService;
import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.CreateBookingRequest;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.DemoAccounts;
import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserResponse;
import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DemoBookingsSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 3, 4);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-03-04T09:00:00Z"), ZoneId.of("Africa/Nairobi"));

    private final UserService userService = mock(UserService.class);
    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final DemoBookingsSeeder seeder = new DemoBookingsSeeder(userService, equipmentService, bookingService, CLOCK);

    private void demoListingsExist() {
        when(userService.getUserIdByEmail(DemoAccounts.OWNER_EMAIL)).thenReturn(Optional.of(1L));
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < DemoBookingsSeeder.PLANS.size(); i++) {
            long id = 100L + i;
            ids.add(id);
            when(equipmentService.getById(id)).thenReturn(new EquipmentResponse(id, 1L,
                    DemoBookingsSeeder.PLANS.get(i).listing(), null, null, null, null, null, null, null));
        }
        when(equipmentService.getIdsByOwner(1L)).thenReturn(ids);
        when(bookingService.createBooking(any(), anyLong())).thenAnswer(call -> {
            CreateBookingRequest r = call.getArgument(0);
            return new BookingResponse(r.equipmentId() + 1000, r.equipmentId(), 2L, r.startDate(), r.endDate(), null,
                    BookingStatus.PENDING, null);
        });
    }

    private void clientBookings(BookingResponse... bookings) {
        when(bookingService.getMyBookings(eq(2L), any()))
                .thenReturn(new PageResponse<>(List.of(bookings), 0, 100, bookings.length, 1));
    }

    @Test
    void firstStart_createsTheDemoClientAndABookingPerPlan_confirmingSome() {
        demoListingsExist();
        when(userService.getUserIdByEmail(DemoAccounts.CLIENT_EMAIL)).thenReturn(Optional.empty());
        when(userService.register(any())).thenReturn(new UserResponse(2L, "Wanjiku Kamau", DemoAccounts.CLIENT_EMAIL,
                "+254711000000", Role.CLIENT, null));
        clientBookings();

        seeder.seed();

        ArgumentCaptor<RegisterRequest> client = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).register(client.capture());
        assertEquals(Role.CLIENT, client.getValue().role());
        ArgumentCaptor<CreateBookingRequest> created = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(bookingService, times(DemoBookingsSeeder.PLANS.size())).createBooking(created.capture(), eq(2L));
        DemoBookingsSeeder.Plan first = DemoBookingsSeeder.PLANS.getFirst();
        assertEquals(TODAY.plusDays(first.startsInDays()), created.getAllValues().getFirst().startDate());
        assertEquals(TODAY.plusDays(first.startsInDays() + first.days() - 1L), created.getAllValues().getFirst().endDate());
        long confirmations = DemoBookingsSeeder.PLANS.stream().filter(DemoBookingsSeeder.Plan::confirm).count();
        verify(bookingService, times((int) confirmations)).confirm(anyLong(), eq(1L));
    }

    @Test
    void demoClientStillHasAnOpenBooking_addsNothing() {
        demoListingsExist();
        when(userService.getUserIdByEmail(DemoAccounts.CLIENT_EMAIL)).thenReturn(Optional.of(2L));
        clientBookings(new BookingResponse(1L, 100L, 2L, TODAY.plusDays(1), TODAY.plusDays(2), null,
                BookingStatus.PENDING, null));

        seeder.seed();

        verify(bookingService, never()).createBooking(any(), anyLong());
    }

    @Test
    void onlyPastOrClosedBookingsLeft_makesAFreshSet() {
        demoListingsExist();
        when(userService.getUserIdByEmail(DemoAccounts.CLIENT_EMAIL)).thenReturn(Optional.of(2L));
        clientBookings(
                new BookingResponse(1L, 100L, 2L, TODAY.minusDays(5), TODAY.minusDays(3), null, BookingStatus.PENDING, null),
                new BookingResponse(2L, 101L, 2L, TODAY.plusDays(4), TODAY.plusDays(5), null, BookingStatus.CANCELLED, null));

        seeder.seed();

        verify(bookingService, times(DemoBookingsSeeder.PLANS.size())).createBooking(any(), eq(2L));
    }

    @Test
    void aBookingThatFails_isSkippedAndTheRestStillMade() {
        demoListingsExist();
        when(userService.getUserIdByEmail(DemoAccounts.CLIENT_EMAIL)).thenReturn(Optional.of(2L));
        clientBookings();
        doThrow(new IllegalStateException("dates taken"))
                .when(bookingService).createBooking(argThat(r -> r != null && r.equipmentId() == 100L), anyLong());

        seeder.seed();

        verify(bookingService, times(DemoBookingsSeeder.PLANS.size())).createBooking(any(), eq(2L));
    }

    @Test
    void noDemoOwner_doesNothing() {
        when(userService.getUserIdByEmail(DemoAccounts.OWNER_EMAIL)).thenReturn(Optional.empty());

        seeder.seed();

        verify(userService, never()).register(any());
        verifyNoInteractions(bookingService);
    }
}
