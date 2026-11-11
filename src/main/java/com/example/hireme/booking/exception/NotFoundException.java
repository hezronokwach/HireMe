package com.example.hireme.booking.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class NotFoundException extends BaseException {
    public NotFoundException() {
        super("Booking not found", HttpStatus.NOT_FOUND);
    }
}
