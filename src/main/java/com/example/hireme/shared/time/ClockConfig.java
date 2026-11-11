package com.example.hireme.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The application's clock, in the business time zone (Africa/Nairobi by default), so "today"
 * means the same day for everyone whatever the server's own time zone is. Inject it instead of
 * calling LocalDate.now(), so tests can use a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Africa/Nairobi}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
