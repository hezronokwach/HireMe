package com.example.hireme.ai.internal;

import com.example.hireme.ai.AiRateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * At most {@code limit} calls per user in any rolling minute. Kept in memory, so it is per app instance
 * and resets on restart; enough to stop one user from burning the shared Groq quota.
 */
class UserRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limit;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> callsByUser = new ConcurrentHashMap<>();

    UserRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    void acquire(Long userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        callsByUser.compute(userId, (id, calls) -> {
            Deque<Instant> recent = calls == null ? new ArrayDeque<>() : calls;
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() >= limit) {
                long retryAfter = Math.max(1, Duration.between(windowStart, recent.peekFirst()).toSeconds());
                throw new AiRateLimitExceededException(limit, retryAfter);
            }
            recent.addLast(now);
            return recent;
        });
    }

    int limit() {
        return limit;
    }
}
