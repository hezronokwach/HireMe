package com.example.hireme.equipment.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class PhotoNotFoundException extends BaseException {
    public PhotoNotFoundException() {
        super("Photo not found", HttpStatus.NOT_FOUND);
    }
}
