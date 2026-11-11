package com.example.hireme.equipment.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseException {
    public ForbiddenException() {
        super("Access Denied: You do not have permission to perform this action", HttpStatus.FORBIDDEN);
    }
}
