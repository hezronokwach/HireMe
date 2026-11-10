package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import com.example.hireme.shared.event.BookingActivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Moves CONFIRMED bookings whose start date has arrived to ACTIVE. Run by {@link BookingActivationJob}.
 */
@Component
@RequiredArgsConstructor
public class BookingActivator {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // Returns how many bookings became ACTIVE.
    @Transactional
    public int activateDueBookings() {
        LocalDate today = LocalDate.now(clock);
        List<BookingEntity> due = bookingRepository.findByStatusAndStartDateLessThanEqual(BookingStatus.CONFIRMED, today);
        int activated = 0;
        for (BookingEntity booking : due) {
            // Conditional update: a booking cancelled since it was read stays cancelled.
            if (bookingRepository.updateStatus(booking.getBookingId(), BookingStatus.CONFIRMED, BookingStatus.ACTIVE) == 1) {
                eventPublisher.publishEvent(new BookingActivatedEvent(booking.getBookingId(), booking.getEquipmentId(), Instant.now(clock)));
                activated++;
            }
        }
        return activated;
    }
}
