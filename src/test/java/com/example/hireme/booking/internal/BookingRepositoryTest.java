package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Runs the real queries on H2 (each test rolls back), since the overlap rule lives in JPQL.
@SpringBootTest
@Transactional
class BookingRepositoryTest {

    private static final long EQUIPMENT_ID = 900L;
    private static final LocalDate DAY_10 = LocalDate.of(2030, 1, 10);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEquipmentBookingChecker bookingChecker;

    private BookingEntity save(long equipmentId, LocalDate start, LocalDate end, BookingStatus status) {
        BookingEntity booking = new BookingEntity();
        booking.setEquipmentId(equipmentId);
        booking.setClientId(1L);
        booking.setStartDate(start);
        booking.setEndDate(end);
        booking.setTotalCostKes(BigDecimal.valueOf(1000));
        booking.setStatus(status);
        booking.setCreationDate(Instant.now());
        return bookingRepository.save(booking);
    }

    // An existing CONFIRMED booking covers the 10th to the 12th, both days included.
    @ParameterizedTest(name = "[{0}, {1}] overlaps: {2}")
    @CsvSource({
            "10, 12, true",   // same dates
            "12, 14, true",   // starts on its last day
            "8, 10, true",    // ends on its first day
            "11, 11, true",   // one day inside it
            "5, 20, true",    // contains it
            "13, 15, false",  // starts the day after it ends
            "7, 9, false"     // ends the day before it starts
    })
    void findOverlapping_countsBothStartAndEndDays(int startDay, int endDay, boolean overlaps) {
        save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2), BookingStatus.CONFIRMED);

        boolean found = !bookingRepository.findOverlapping(EQUIPMENT_ID,
                DAY_10.withDayOfMonth(startDay), DAY_10.withDayOfMonth(endDay),
                BookingServiceImpl.BLOCKING_STATUSES).isEmpty();

        assertEquals(overlaps, found);
    }

    @Test
    void findOverlapping_ignoresOtherStatusesAndOtherEquipment() {
        save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2), BookingStatus.PENDING);
        save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2), BookingStatus.CANCELLED);
        save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2), BookingStatus.COMPLETED);
        save(EQUIPMENT_ID + 1, DAY_10, DAY_10.plusDays(2), BookingStatus.CONFIRMED);

        assertTrue(bookingRepository.findOverlapping(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2),
                BookingServiceImpl.BLOCKING_STATUSES).isEmpty());
    }

    @Test
    void findOverlapping_activeBookingsBlockDates() {
        save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(2), BookingStatus.ACTIVE);

        assertEquals(1, bookingRepository.findOverlapping(EQUIPMENT_ID, DAY_10, DAY_10,
                BookingServiceImpl.BLOCKING_STATUSES).size());
    }

    @Test
    void findOverlapping_returnsBookingsInStartDateOrder() {
        BookingEntity later = save(EQUIPMENT_ID, DAY_10.plusDays(5), DAY_10.plusDays(6), BookingStatus.PENDING);
        BookingEntity earlier = save(EQUIPMENT_ID, DAY_10, DAY_10.plusDays(1), BookingStatus.PENDING);

        assertEquals(List.of(earlier, later), bookingRepository.findOverlapping(EQUIPMENT_ID,
                DAY_10, DAY_10.plusDays(10), EnumSet.of(BookingStatus.PENDING)));
    }

    @Test
    void hasOpenBookings_trueForPendingOrConfirmedEndingTodayOrLater() {
        LocalDate today = LocalDate.now();
        save(EQUIPMENT_ID, today.minusDays(2), today, BookingStatus.PENDING);

        assertTrue(bookingChecker.hasOpenBookings(EQUIPMENT_ID));
    }

    @Test
    void hasOpenBookings_falseForPastOrClosedBookings() {
        LocalDate today = LocalDate.now();
        save(EQUIPMENT_ID, today.minusDays(5), today.minusDays(1), BookingStatus.CONFIRMED);
        save(EQUIPMENT_ID, today.plusDays(1), today.plusDays(3), BookingStatus.CANCELLED);
        save(EQUIPMENT_ID, today.plusDays(1), today.plusDays(3), BookingStatus.COMPLETED);

        assertFalse(bookingChecker.hasOpenBookings(EQUIPMENT_ID));
    }
}
