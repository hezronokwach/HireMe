package com.example.hireme.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A booking.
 *
 * @param clientName  who booked; only for the equipment's owner (or an admin), otherwise null
 * @param clientPhone the client's phone number; only for the owner (or an admin), and only while the booking
 *                    is PENDING, CONFIRMED or ACTIVE, otherwise null
 */
public record BookingResponse(
        Long bookingId,
        Long equipmentId,
        Long clientId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalCostKes,
        BookingStatus status,
        Instant creationDate,
        String clientName,
        String clientPhone
) {
    /** A booking without the client's contact details. */
    public BookingResponse(Long bookingId, Long equipmentId, Long clientId, LocalDate startDate, LocalDate endDate,
                           BigDecimal totalCostKes, BookingStatus status, Instant creationDate) {
        this(bookingId, equipmentId, clientId, startDate, endDate, totalCostKes, status, creationDate, null, null);
    }

    public BookingResponse withClient(String name, String phone) {
        return new BookingResponse(bookingId, equipmentId, clientId, startDate, endDate, totalCostKes, status,
                creationDate, name, phone);
    }
}
