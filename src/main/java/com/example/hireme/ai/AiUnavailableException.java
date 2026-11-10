package com.example.hireme.ai;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

// AI is turned off, or Groq failed (down, timed out, rate-limited us, or gave an unusable answer).
public class AiUnavailableException extends BaseException {

    public static final String MESSAGE = "AI features are unavailable right now. Please try again later.";

    public AiUnavailableException() {
        super(MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public AiUnavailableException(Throwable cause) {
        this();
        initCause(cause);
    }
}
