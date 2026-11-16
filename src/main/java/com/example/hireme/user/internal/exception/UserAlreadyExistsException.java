package com.example.hireme.user.internal.exception;

import com.example.hireme.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** 409: the email or phone number already belongs to an account. Names the field, so a form can show it there. */
public class UserAlreadyExistsException extends BaseException {

    private UserAlreadyExistsException(String field, String message) {
        super(message, HttpStatus.CONFLICT, Map.of(field, message));
    }

    public static UserAlreadyExistsException email() {
        return new UserAlreadyExistsException("email", "This email is already registered. Log in instead.");
    }

    public static UserAlreadyExistsException phoneNumber() {
        return new UserAlreadyExistsException("phoneNumber",
                "This phone number is already registered. Log in instead, or use another number.");
    }
}
