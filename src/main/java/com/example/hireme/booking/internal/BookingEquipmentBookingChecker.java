package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import com.example.hireme.equipment.EquipmentBookingChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingEquipmentBookingChecker implements EquipmentBookingChecker {

    private static final Set<BookingStatus> OPEN_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Override
    public boolean hasOpenBookings(Long equipmentId) {
        return bookingRepository.existsByEquipmentIdAndStatusInAndEndDateGreaterThanEqual(
                equipmentId, OPEN_STATUSES, LocalDate.now(clock));
    }

    @Override
    public Set<Long> findBookedEquipmentIds(LocalDate from, LocalDate to) {
        return Set.copyOf(bookingRepository.findEquipmentIdsBookedBetween(from, to, BookingServiceImpl.BLOCKING_STATUSES));
    }
}
