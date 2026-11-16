package com.example.hireme.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public abstract class BaseException extends RuntimeException {
    private final HttpStatus status;
    private final String message;
    /** Messages for single request fields (e.g. {@code phoneNumber}), or null; sent as the response's {@code errors}. */
    private final Map<String, String> errors;

    protected BaseException(String message, HttpStatus status) {
        this(message, status, null);
    }

    protected BaseException(String message, HttpStatus status, Map<String, String> errors) {
        super(message);
        this.message = message;
        this.status = status;
        this.errors = errors;
    }
}
