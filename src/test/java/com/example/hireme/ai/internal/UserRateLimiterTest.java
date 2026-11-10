package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class UserRateLimiterTest {

    // A clock the test moves forward by hand.
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2030-01-15T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final UserRateLimiter limiter = new UserRateLimiter(3, clock);

    @Test
    void allowsUpToTheLimitThenRefuses() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        AiRateLimitExceededException ex = assertThrows(AiRateLimitExceededException.class, () -> limiter.acquire(1L));
        assertEquals("Too many AI requests: the limit is 3 per minute. Try again in 60 seconds.", ex.getMessage());
    }

    @Test
    void windowRollsForward() {
        limiter.acquire(1L);
        clock.advance(Duration.ofSeconds(30));
        limiter.acquire(1L);
        limiter.acquire(1L);
        assertThrows(AiRateLimitExceededException.class, () -> limiter.acquire(1L));

        clock.advance(Duration.ofSeconds(31)); // the first call is now more than a minute old

        assertDoesNotThrow(() -> limiter.acquire(1L));
        AiRateLimitExceededException ex = assertThrows(AiRateLimitExceededException.class, () -> limiter.acquire(1L));
        assertTrue(ex.getMessage().endsWith("Try again in 29 seconds."), ex.getMessage());
    }

    @Test
    void refusedCallsDontCount() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);
        assertThrows(AiRateLimitExceededException.class, () -> limiter.acquire(1L));
        assertThrows(AiRateLimitExceededException.class, () -> limiter.acquire(1L));

        clock.advance(Duration.ofSeconds(61));

        assertDoesNotThrow(() -> limiter.acquire(1L));
    }

    @Test
    void usersHaveSeparateLimits() {
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        assertDoesNotThrow(() -> limiter.acquire(2L));
    }
}
