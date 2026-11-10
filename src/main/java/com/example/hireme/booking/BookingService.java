package com.example.hireme.booking;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest createBookingRequest, Long currentClientId);
    BookingResponse getById(Long bookingId, Long currentUserId);
    List<BookingResponse> getMyBookings(Long currentUserId);
    List<BookingResponse> getBookingsForMyEquipment(Long currentOwnerId, BookingStatus status); // status is optional
    EquipmentAvailabilityResponse getAvailability(Long equipmentId, LocalDate from, LocalDate to);
    BookingResponse confirm(Long bookingId, Long currentOwnerId);  // owner action
    BookingResponse cancel(Long bookingId, Long currentUserId);    // client or owner
    BookingResponse complete(Long bookingId, Long currentOwnerId); // owner action
}
