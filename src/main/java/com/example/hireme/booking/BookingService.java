package com.example.hireme.booking;

import com.example.hireme.booking.dto.BookingResponse;
import com.example.hireme.booking.dto.CreateBookingRequest;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest createBookingRequest, Long currentClientId);
    BookingResponse getById(Long bookingId, Long currentUserId);
    List<BookingResponse> getMyBookings(Long currentUserId);
    BookingResponse confirm(Long bookingId, Long currentOwnerId);  // owner action
    BookingResponse cancel(Long bookingId, Long currentUserId);    // client or owner
    BookingResponse complete(Long bookingId, Long currentOwnerId);  // owner actio
}
