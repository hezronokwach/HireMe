package com.example.hireme.booking;

import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Bookings and their lifecycle: PENDING → CONFIRMED → ACTIVE → COMPLETED, or CANCELLED")
public class BookingController {

    static final Set<String> SORTABLE_FIELDS = Set.of("startDate", "creationDate");
    static final String DEFAULT_SORT = "creationDate,desc"; // newest requests first
    private static final String SORT_DESCRIPTION = "`startDate` or `creationDate`, optionally followed by `,asc` or `,desc`. "
            + "Default `" + DEFAULT_SORT + "` (newest first).";

    private final BookingService bookingService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(operationId = "createBooking", summary = "Book equipment for a date range (CLIENT, ADMIN)",
            description = "Starts as PENDING. Both start and end dates are booked days and both are charged.")
    @ApiResponse(responseCode = "201", description = "Booking request created")
    @ApiResponse(responseCode = "404", description = "No such equipment")
    @ApiResponse(responseCode = "409", description = "Listing under maintenance, or dates already booked")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long clientId = getUserId(userDetails);
        return new ResponseEntity<>(bookingService.createBooking(request, clientId), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getBooking", summary = "Get a booking (its client or the equipment owner)")
    @ApiResponse(responseCode = "200", description = "The booking")
    @ApiResponse(responseCode = "404", description = "No such booking")
    public ResponseEntity<BookingResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getById(id, userId));
    }

    @GetMapping("/my-bookings")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(operationId = "getMyBookings", summary = "The caller's bookings, one page at a time")
    public ResponseEntity<PageResponse<BookingResponse>> getMyBookings(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to " + PageRequests.MAX_SIZE)
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = SORT_DESCRIPTION, example = "startDate,asc")
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal User userDetails
    ) {
        Long clientId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getMyBookings(clientId, pageRequest(page, size, sort)));
    }

    @GetMapping("/for-my-equipment")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "getBookingsForMyEquipment", summary = "Bookings on the caller's equipment, one page at a time")
    public ResponseEntity<PageResponse<BookingResponse>> getBookingsForMyEquipment(
            @Parameter(description = "Only bookings with this status, e.g. PENDING for requests waiting for you")
            @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to " + PageRequests.MAX_SIZE)
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = SORT_DESCRIPTION, example = "startDate,asc")
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal User userDetails
    ) {
        Long ownerId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.getBookingsForMyEquipment(ownerId, status, pageRequest(page, size, sort)));
    }

    private Pageable pageRequest(int page, int size, String sort) {
        return PageRequests.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT, "bookingId");
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "confirmBooking", summary = "Confirm a PENDING booking (equipment owner)",
            description = "Re-checks the dates, then cancels the other PENDING bookings that overlap them.")
    @ApiResponse(responseCode = "200", description = "Now CONFIRMED")
    @ApiResponse(responseCode = "404", description = "No such booking")
    @ApiResponse(responseCode = "409", description = "Not PENDING, dates already booked, or changed by someone else meanwhile")
    public ResponseEntity<BookingResponse> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long ownerId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.confirm(id, ownerId));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "cancelBooking", summary = "Cancel a booking (its client or the equipment owner)",
            description = "Once a booking is ACTIVE only the owner can cancel it.")
    @ApiResponse(responseCode = "200", description = "Now CANCELLED")
    @ApiResponse(responseCode = "404", description = "No such booking")
    @ApiResponse(responseCode = "409", description = "Already COMPLETED or CANCELLED, or changed by someone else meanwhile")
    public ResponseEntity<BookingResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(bookingService.cancel(id, userId));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "completeBooking", summary = "Complete a CONFIRMED or ACTIVE booking (equipment owner)")
    @ApiResponse(responseCode = "200", description = "Now COMPLETED")
    @ApiResponse(responseCode = "404", description = "No such booking")
    @ApiResponse(responseCode = "409", description = "Not CONFIRMED or ACTIVE, or changed by someone else meanwhile")
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
