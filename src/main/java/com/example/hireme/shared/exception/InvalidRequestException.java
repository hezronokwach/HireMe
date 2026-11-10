package com.example.hireme.shared.exception;

import org.springframework.http.HttpStatus;

// A request parameter that is well-formed but not allowed, e.g. size=500 or an unknown sort field.
public class InvalidRequestException extends BaseException {
    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
