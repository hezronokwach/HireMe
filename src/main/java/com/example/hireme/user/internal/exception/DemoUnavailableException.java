package com.example.hireme.user.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DemoUnavailableException extends BaseException {
    public DemoUnavailableException() {
        super("The demo isn't set up on this server", HttpStatus.NOT_FOUND);
    }
}
