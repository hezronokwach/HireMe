package com.example.hireme.ai;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

// This user made too many AI calls in the last minute; protects the shared Groq quota.
public class AiRateLimitExceededException extends BaseException {
    public AiRateLimitExceededException(int limitPerMinute, long retryAfterSeconds) {
        super("Too many AI requests: the limit is " + limitPerMinute + " per minute. Try again in "
                + retryAfterSeconds + " seconds.", HttpStatus.TOO_MANY_REQUESTS);
    }
}
