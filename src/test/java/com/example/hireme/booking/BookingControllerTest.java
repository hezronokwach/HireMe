package com.example.hireme.booking;

import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;
    @Mock
    private UserService userService;

    @InjectMocks
    private BookingController bookingController;

    private User userDetails() {
        return new User("john@test.com", "password", List.of());
    }

    private BookingResponse bookingResponse() {
        return new BookingResponse(1L, 1L, 1L, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3), BigDecimal.valueOf(3000), BookingStatus.PENDING, Instant.now());
    }

    @Test
    void createBooking_validRequest_returnsCreated() {
        CreateBookingRequest request = new CreateBookingRequest(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        when(bookingService.createBooking(request, 1L)).thenReturn(bookingResponse());

        ResponseEntity<BookingResponse> result = bookingController.createBooking(request, userDetails());

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
    }

    @Test
    void getById_existingBooking_returnsOk() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        when(bookingService.getById(1L, 1L)).thenReturn(bookingResponse());

        ResponseEntity<BookingResponse> result = bookingController.getById(1L, userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void getMyBookings_returnsList() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        when(bookingService.getMyBookings(1L)).thenReturn(List.of(bookingResponse()));

        ResponseEntity<List<BookingResponse>> result = bookingController.getMyBookings(userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void confirm_booking_returnsOk() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        BookingResponse confirmed = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.CONFIRMED, null);
        when(bookingService.confirm(1L, 1L)).thenReturn(confirmed);

        ResponseEntity<BookingResponse> result = bookingController.confirm(1L, userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(BookingStatus.CONFIRMED, result.getBody().status());
    }

    @Test
    void cancel_booking_returnsOk() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        BookingResponse cancelled = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.CANCELLED, null);
        when(bookingService.cancel(1L, 1L)).thenReturn(cancelled);

        ResponseEntity<BookingResponse> result = bookingController.cancel(1L, userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(BookingStatus.CANCELLED, result.getBody().status());
    }

    @Test
    void complete_booking_returnsOk() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.of(1L));
        BookingResponse completed = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.COMPLETED, null);
        when(bookingService.complete(1L, 1L)).thenReturn(completed);

        ResponseEntity<BookingResponse> result = bookingController.complete(1L, userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(BookingStatus.COMPLETED, result.getBody().status());
    }

    @Test
    void getUserId_userNotFound_throwsRuntimeException() {
        when(userService.getUserIdByEmail("john@test.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> bookingController.createBooking(
                new CreateBookingRequest(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3)),
                userDetails()));
    }
}
