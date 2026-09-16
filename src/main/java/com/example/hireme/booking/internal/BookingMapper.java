package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingResponse;
import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.CreateBookingRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class BookingMapper {
    public BookingEntity toBookingEntity(
            CreateBookingRequest createBookingRequest,
            Long clientId,
            BigDecimal totalCostKes
    ) {
        BookingEntity bookingEntity = new BookingEntity();
        bookingEntity.setEquipmentId(createBookingRequest.equipmentId());
        bookingEntity.setClientId(clientId);
        bookingEntity.setStartDate(createBookingRequest.startDate());
        bookingEntity.setEndDate(createBookingRequest.endDate());
        bookingEntity.setTotalCostKes(totalCostKes);
        bookingEntity.setStatus(BookingStatus.PENDING);
        bookingEntity.setCreationDate(Instant.now());
        return bookingEntity;
    }

    public BookingResponse toBookingResponse(BookingEntity bookingEntity) {
        return new BookingResponse(
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
