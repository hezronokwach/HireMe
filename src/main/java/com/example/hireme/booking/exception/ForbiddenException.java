package com.example.hireme.booking.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseException {
    public ForbiddenException() {
        super("You are not authorized to perform this action", HttpStatus.CONFLICT);
    }
}
