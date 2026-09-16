package com.example.hireme.booking;

import com.example.hireme.booking.dto.BookingResponse;
import com.example.hireme.booking.dto.CreateBookingRequest;
import com.example.hireme.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long clientId = getUserId(userDetails);
        return new ResponseEntity<>(bookingService.createBooking(request, clientId), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getById(id, userId));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal User userDetails
    ) {
        Long clientId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getMyBookings(clientId));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<BookingResponse> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long ownerId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.confirm(id, ownerId));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.cancel(id, userId));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<BookingResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long ownerId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.complete(id, ownerId));
    }

    private Long getUserId(User userDetails) {
        return userService.getUserIdByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
    }
}
