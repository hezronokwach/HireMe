package com.example.hireme.booking.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingActivationJobTest {

    @Mock
    private BookingActivator bookingActivator;

    @InjectMocks
    private BookingActivationJob job;

    @Test
    void activateHourly_runsActivator() {
        job.activateHourly();

        verify(bookingActivator).activateDueBookings();
    }

    @Test
    void activateOnStartup_runsActivator() {
        job.activateOnStartup();

        verify(bookingActivator).activateDueBookings();
    }
}
