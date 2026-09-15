package com.example.hireme.equipment.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ConflictException extends BaseException {

    protected ConflictException() {
        super("Cannot edit equipment", HttpStatus.CONFLICT);
    }
}
