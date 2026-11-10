package com.example.hireme.booking.internal;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Activates bookings that have reached their start date: every hour (at minute 5, in the business
 * time zone), and once on startup so nothing waits a whole hour after a restart or downtime.
 * Running it again is harmless: only CONFIRMED bookings are touched.
 */
@Component
@RequiredArgsConstructor
public class BookingActivationJob {

    private static final Logger log = LoggerFactory.getLogger(BookingActivationJob.class);

    private final BookingActivator bookingActivator;

    @Scheduled(cron = "${app.bookings.activation-cron:0 5 * * * *}", zone = "${app.time-zone:Africa/Nairobi}")
    public void activateHourly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void activateOnStartup() {
        run();
    }

    private void run() {
        int activated = bookingActivator.activateDueBookings();
        if (activated > 0) {
            log.info("Activated {} booking(s) that reached their start date", activated);
        }
    }
}
