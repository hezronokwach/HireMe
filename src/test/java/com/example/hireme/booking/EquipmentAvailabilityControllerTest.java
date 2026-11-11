package com.example.hireme.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentAvailabilityControllerTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);

    @Mock
    private BookingService bookingService;

    private EquipmentAvailabilityController controller;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(NAIROBI).toInstant(), NAIROBI);
        controller = new EquipmentAvailabilityController(bookingService, fixedClock);
    }

    @Test
    void getAvailability_withDates_passesThem() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);
        EquipmentAvailabilityResponse response = new EquipmentAvailabilityResponse(1L, from, to, true, List.of());
        when(bookingService.getAvailability(1L, from, to)).thenReturn(response);

        ResponseEntity<EquipmentAvailabilityResponse> result = controller.getAvailability(1L, from, to);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(response, result.getBody());
    }

    @Test
    void getAvailability_withoutDates_defaultsToNext90DaysFromToday() {
        LocalDate end = TODAY.plusDays(EquipmentAvailabilityController.DEFAULT_RANGE_DAYS - 1L);
        when(bookingService.getAvailability(1L, TODAY, end))
                .thenReturn(new EquipmentAvailabilityResponse(1L, TODAY, end, true, List.of()));

        controller.getAvailability(1L, null, null);

        verify(bookingService).getAvailability(1L, TODAY, end);
    }
}
