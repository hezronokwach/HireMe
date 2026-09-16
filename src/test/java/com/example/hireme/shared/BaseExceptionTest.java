package com.example.hireme.shared;

import com.example.hireme.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class BaseExceptionTest {

    private static class TestException extends BaseException {
        TestException(String message, HttpStatus status) {
            super(message, status);
        }
    }

    @Test
    void constructor_setsMessageAndStatus() {
        TestException ex = new TestException("Test error", HttpStatus.BAD_REQUEST);

        assertEquals("Test error", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void constructor_withConflictStatus() {
        TestException ex = new TestException("Conflict", HttpStatus.CONFLICT);

        assertEquals("Conflict", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }
}
