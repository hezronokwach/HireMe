package com.example.hireme.shared;

import com.example.hireme.booking.exception.ConflictException;
import com.example.hireme.booking.exception.ForbiddenException;
import com.example.hireme.booking.exception.NotFoundException;
import com.example.hireme.booking.exception.ValidationException;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import com.example.hireme.user.internal.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionClassesTest {

    @Test
    void conflictException_setsMessageAndStatus() {
        ConflictException ex = new ConflictException("Test conflict");
        assertEquals("Test conflict", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void bookingForbiddenException_setsMessageAndStatus() {
        ForbiddenException ex = new ForbiddenException();
        assertEquals("You are not authorized to perform this action", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void notFoundException_setsMessageAndStatus() {
        NotFoundException ex = new NotFoundException();
        assertEquals("Booking not found", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void validationException_setsMessageAndStatus() {
        ValidationException ex = new ValidationException("Invalid input");
        assertEquals("Invalid input", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void equipmentNotFoundException_setsMessageAndStatus() {
        EquipmentNotFoundException ex = new EquipmentNotFoundException();
        assertEquals("Equipment not found", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void equipmentForbiddenException_setsMessageAndStatus() {
        com.example.hireme.equipment.internal.exception.ForbiddenException ex =
                new com.example.hireme.equipment.internal.exception.ForbiddenException();
        assertEquals("Access Denied: You do not have permission to perform this action", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void invalidCredentialsException_setsMessageAndStatus() {
        InvalidCredentialsException ex = new InvalidCredentialsException();
        assertEquals("Invalid email or password", ex.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void userAlreadyExistsException_setsMessageAndStatus() {
        UserAlreadyExistsException email = UserAlreadyExistsException.email();
        assertEquals("This email is already registered. Log in instead.", email.getMessage());
        assertEquals(HttpStatus.CONFLICT, email.getStatus());
        assertEquals(java.util.Map.of("email", email.getMessage()), email.getErrors());

        UserAlreadyExistsException phone = UserAlreadyExistsException.phoneNumber();
        assertEquals(HttpStatus.CONFLICT, phone.getStatus());
        assertTrue(phone.getErrors().get("phoneNumber").startsWith("This phone number is already registered"));
    }

    @Test
    void bookingForbiddenException_withMessage_setsMessageAndStatus() {
        ForbiddenException ex = new ForbiddenException("Only the owner can do this");
        assertEquals("Only the owner can do this", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }
}
