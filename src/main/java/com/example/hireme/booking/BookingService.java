package com.example.hireme.booking;

import com.example.hireme.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest createBookingRequest, Long currentClientId);
    BookingResponse getById(Long bookingId, Long currentUserId);
    PageResponse<BookingResponse> getMyBookings(Long currentUserId, Pageable pageable);
    PageResponse<BookingResponse> getBookingsForMyEquipment(Long currentOwnerId, BookingStatus status, Pageable pageable); // status is optional
    EquipmentAvailabilityResponse getAvailability(Long equipmentId, LocalDate from, LocalDate to);
    BookingResponse confirm(Long bookingId, Long currentOwnerId);  // owner action
    BookingResponse cancel(Long bookingId, Long currentUserId);    // client or owner
    BookingResponse complete(Long bookingId, Long currentOwnerId); // owner action
}
