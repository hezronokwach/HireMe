package com.example.hireme.booking.internal;

import com.example.hireme.booking.dto.BookingResponse;
import com.example.hireme.booking.dto.CreateBookingRequest;

import java.math.BigDecimal;
import java.time.Instant;

public class BookingMapper {
    public BookingEntity toBookingEntity
            (CreateBookingRequest createBookingRequest,
             Long clientId,
             BigDecimal totalCostKes
            )
    {
        BookingEntity bookingEntity = new BookingEntity();
        bookingEntity.setEquipmentId(createBookingRequest.equipmentId());
        bookingEntity.setClientId(clientId);
        bookingEntity.setStartDate(createBookingRequest.startDate());
        bookingEntity.setEndDate(createBookingRequest.endDate());
        bookingEntity.setTotalCostKes(totalCostKes);
        bookingEntity.setStatus(BookingEntity.BookingStatus.PENDING);
        bookingEntity.setCreationDate(Instant.now());
        return bookingEntity;

    }

    public BookingResponse toBookingResponse(BookingEntity bookingEntity) {
        return  new BookingResponse(
                bookingEntity.getBookingId(),
                bookingEntity.getEquipmentId(),
                bookingEntity.getClientId(),
                bookingEntity.getStartDate(),
                bookingEntity.getEndDate(),
                bookingEntity.getTotalCostKes(),
                bookingEntity.getStatus(),
                bookingEntity.getCreationDate()
        );
    }
}
