package com.example.hireme.booking.internal;

import com.example.hireme.booking.BookingResponse;
import com.example.hireme.booking.BookingStatus;
import com.example.hireme.booking.CreateBookingRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BookingMapperTest {

    private final BookingMapper bookingMapper = new BookingMapper();

    @Test
    void toBookingEntity_mapsAllFields() {
        CreateBookingRequest request = new CreateBookingRequest(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        BookingEntity entity = bookingMapper.toBookingEntity(request, 1L, BigDecimal.valueOf(3000));

        assertEquals(1L, entity.getEquipmentId());
        assertEquals(1L, entity.getClientId());
        assertEquals(LocalDate.now().plusDays(1), entity.getStartDate());
        assertEquals(LocalDate.now().plusDays(3), entity.getEndDate());
        assertEquals(BigDecimal.valueOf(3000), entity.getTotalCostKes());
        assertEquals(BookingStatus.PENDING, entity.getStatus());
        assertNotNull(entity.getCreationDate());
    }

    @Test
    void toBookingResponse_mapsAllFields() {
        BookingEntity entity = new BookingEntity();
        entity.setBookingId(1L);
        entity.setEquipmentId(2L);
        entity.setClientId(3L);
        entity.setStartDate(LocalDate.now().plusDays(1));
        entity.setEndDate(LocalDate.now().plusDays(3));
        entity.setTotalCostKes(BigDecimal.valueOf(3000));
        entity.setStatus(BookingStatus.CONFIRMED);
        Instant now = Instant.now();
        entity.setCreationDate(now);

        BookingResponse response = bookingMapper.toBookingResponse(entity);

        assertEquals(1L, response.bookingId());
        assertEquals(2L, response.equipmentId());
        assertEquals(3L, response.clientId());
        assertEquals(LocalDate.now().plusDays(1), response.startDate());
        assertEquals(LocalDate.now().plusDays(3), response.endDate());
        assertEquals(BigDecimal.valueOf(3000), response.totalCostKes());
        assertEquals(BookingStatus.CONFIRMED, response.status());
        assertEquals(now, response.creationDate());
    }
}
