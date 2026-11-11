package com.example.hireme.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;

// Lives in the booking module because booked dates come from bookings; equipment can't depend on booking.
@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
@Tag(name = "Equipment")
public class EquipmentAvailabilityController {

    static final int DEFAULT_RANGE_DAYS = 90;

    private final BookingService bookingService;
    private final Clock clock;

    @GetMapping("/{equipmentId}/availability")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getEquipmentAvailability", summary = "Booked dates of a listing",
            description = "Confirmed or active bookings overlapping [from, to]. At most 366 days.")
    @ApiResponse(responseCode = "200", description = "Booked periods in the range")
    @ApiResponse(responseCode = "404", description = "No such equipment")
    public ResponseEntity<EquipmentAvailabilityResponse> getAvailability(
            @PathVariable Long equipmentId,
            @Parameter(description = "First day, ISO date. Default today (Africa/Nairobi)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, included. Default " + DEFAULT_RANGE_DAYS + " days from `from`")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate start = from != null ? from : LocalDate.now(clock);
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS - 1L);
        return ResponseEntity.ok(bookingService.getAvailability(equipmentId, start, end));
    }
}
