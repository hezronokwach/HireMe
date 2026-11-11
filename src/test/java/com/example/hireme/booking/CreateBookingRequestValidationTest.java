package com.example.hireme.booking;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ValidatorFactory;

import static org.junit.jupiter.api.Assertions.*;

class CreateBookingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> problems(LocalDate start, LocalDate end) {
        return validator.validate(new CreateBookingRequest(1L, start, end)).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void oneDayBookingForToday_isValid() {
        LocalDate today = LocalDate.now();
        assertEquals(Set.of(), problems(today, today));
    }

    @Test
    void futureRange_isValid() {
        LocalDate today = LocalDate.now();
        assertEquals(Set.of(), problems(today.plusDays(3), today.plusDays(5)));
    }

    @Test
    void pastDays_areRejected() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertEquals(Set.of("Start date must be today or in the future", "End date must be today or in the future"),
                problems(yesterday, yesterday));
    }

    @Test
    void missingDates_areRejected() {
        assertEquals(Set.of("Start date is required", "End date is required"), problems(null, null));
    }
}
