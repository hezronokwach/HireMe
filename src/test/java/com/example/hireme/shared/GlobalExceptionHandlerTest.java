package com.example.hireme.shared;

import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.exception.ConflictException;
import com.example.hireme.shared.exception.ErrorResponse;
import com.example.hireme.shared.exception.GlobalExceptionHandler;
import com.example.hireme.user.internal.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Test
    void handleBaseException_conflictException_returnsConflict() {
        ConflictException ex = new ConflictException("Booking conflict");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBaseException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Booking conflict", response.getBody().message());
    }

    @Test
    void handleBaseException_invalidCredentials_returnsUnauthorized() {
        InvalidCredentialsException ex = new InvalidCredentialsException();

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBaseException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid email or password", response.getBody().message());
    }

    @Test
    void handleValidationExceptions_returnsBadRequest() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "email", "Email is required");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                (MethodParameter) null, bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().message());
        assertEquals("Email is required", response.getBody().errors().get("email"));
    }

    @Test
    void handleTypeMismatchException_returnsBadRequest() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "FOO", BookingStatus.class, "status", null, null);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatchException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid value 'FOO' for parameter 'status'", response.getBody().message());
    }

    @Test
    void handleAccessDeniedException_returnsForbidden() {
        AuthorizationDeniedException ex = new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().status());
    }

    @Test
    void handleGeneralException_returnsInternalServerError() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().message().contains("Something went wrong"));
    }
}
