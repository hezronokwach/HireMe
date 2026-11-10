package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingStatus;
import com.example.hireme.shared.event.BookingActivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingActivatorTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    // 00:30 in Nairobi on the 15th is still the 14th in UTC: "today" must follow the business time zone.
    private static final ZonedDateTime NOW = ZonedDateTime.of(2030, 1, 15, 0, 30, 0, 0, NAIROBI);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingActivator bookingActivator;

    @BeforeEach
    void setUp() {
        bookingActivator = new BookingActivator(bookingRepository, eventPublisher, Clock.fixed(NOW.toInstant(), NAIROBI));
    }

    private BookingEntity confirmed(long bookingId) {
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(bookingId);
        booking.setEquipmentId(10L + bookingId);
        booking.setStartDate(TODAY);
        booking.setEndDate(TODAY.plusDays(2));
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }

    @Test
    void activateDueBookings_usesTodayInTheBusinessTimeZone() {
        bookingActivator.activateDueBookings();

        verify(bookingRepository).findByStatusAndStartDateLessThanEqual(BookingStatus.CONFIRMED, TODAY);
    }

    @Test
    void activateDueBookings_activatesEachDueBookingAndPublishesEvent() {
        when(bookingRepository.findByStatusAndStartDateLessThanEqual(BookingStatus.CONFIRMED, TODAY))
                .thenReturn(List.of(confirmed(1L), confirmed(2L)));
        when(bookingRepository.updateStatus(any(), eq(BookingStatus.CONFIRMED), eq(BookingStatus.ACTIVE))).thenReturn(1);

        int activated = bookingActivator.activateDueBookings();

        assertEquals(2, activated);
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        BookingActivatedEvent first = assertInstanceOf(BookingActivatedEvent.class, events.getAllValues().get(0));
        assertEquals(1L, first.bookingId());
        assertEquals(11L, first.equipmentId());
        assertEquals(NOW.toInstant(), first.occurredAt());
    }

    @Test
    void activateDueBookings_bookingChangedMeanwhile_isSkipped() {
        when(bookingRepository.findByStatusAndStartDateLessThanEqual(BookingStatus.CONFIRMED, TODAY))
                .thenReturn(List.of(confirmed(1L)));
        when(bookingRepository.updateStatus(1L, BookingStatus.CONFIRMED, BookingStatus.ACTIVE)).thenReturn(0);

        assertEquals(0, bookingActivator.activateDueBookings());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void activateDueBookings_nothingDue_doesNothing() {
        assertEquals(0, bookingActivator.activateDueBookings());
        verify(bookingRepository, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }
}
