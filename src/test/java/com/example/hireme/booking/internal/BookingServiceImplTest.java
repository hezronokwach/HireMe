package com.example.hireme.booking.internal;

import com.example.hireme.booking.*;
import com.example.hireme.booking.exception.ConflictException;
import com.example.hireme.booking.exception.ForbiddenException;
import com.example.hireme.booking.exception.NotFoundException;
import com.example.hireme.booking.exception.ValidationException;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.shared.event.BookingCancelledEvent;
import com.example.hireme.shared.event.BookingCompletedEvent;
import com.example.hireme.shared.event.BookingCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private EquipmentService equipmentService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private CreateBookingRequest createRequest() {
        return new CreateBookingRequest(1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
    }

    private BookingEntity createEntity(BookingStatus status) {
        BookingEntity entity = new BookingEntity();
        entity.setBookingId(1L);
        entity.setEquipmentId(1L);
        entity.setClientId(1L);
        entity.setStatus(status);
        entity.setStartDate(LocalDate.now().plusDays(1));
        entity.setEndDate(LocalDate.now().plusDays(3));
        entity.setTotalCostKes(BigDecimal.valueOf(3000));
        entity.setCreationDate(Instant.now());
        return entity;
    }

    private EquipmentResponse equipmentResponse(Long ownerId) {
        return new EquipmentResponse(1L, ownerId, "Excavator", com.example.hireme.equipment.Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi", EquipmentStatus.AVAILABLE, Instant.now(), null);
    }

    @Test
    void createBooking_validRequest_returnsResponse() {
        CreateBookingRequest request = createRequest();
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        BookingResponse response = new BookingResponse(1L, 1L, 1L, LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3), BigDecimal.valueOf(3000), BookingStatus.PENDING, Instant.now());

        when(equipmentService.isAvailable(1L)).thenReturn(true);
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        when(bookingMapper.toBookingEntity(request, 1L, BigDecimal.valueOf(3000))).thenReturn(entity);
        when(bookingRepository.save(entity)).thenReturn(entity);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.createBooking(request, 1L);

        assertNotNull(result);
        verify(eventPublisher).publishEvent(any(BookingCreatedEvent.class));
    }

    @Test
    void createBooking_equipmentNotAvailable_throwsConflict() {
        when(equipmentService.isAvailable(1L)).thenReturn(false);

        assertThrows(ConflictException.class, () -> bookingService.createBooking(createRequest(), 1L));
    }

    @Test
    void createBooking_endDateBeforeStartDate_throwsValidation() {
        CreateBookingRequest request = new CreateBookingRequest(1L, LocalDate.now().plusDays(5), LocalDate.now().plusDays(2));
        when(equipmentService.isAvailable(1L)).thenReturn(true);

        assertThrows(ValidationException.class, () -> bookingService.createBooking(request, 1L));
    }

    @Test
    void getById_clientOwner_returnsBooking() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.getById(1L, 1L);

        assertNotNull(result);
    }

    @Test
    void getById_equipmentOwner_returnsBooking() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.getById(1L, 2L);

        assertNotNull(result);
    }

    @Test
    void getById_unauthorizedUser_throwsForbidden() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        assertThrows(ForbiddenException.class, () -> bookingService.getById(1L, 3L));
    }

    @Test
    void getById_notFound_throwsNotFoundException() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingService.getById(1L, 1L));
    }

    @Test
    void getMyBookings_returnsList() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findByClientId(1L)).thenReturn(List.of(entity));
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        List<BookingResponse> result = bookingService.getMyBookings(1L);

        assertEquals(1, result.size());
    }

    @Test
    void confirm_pendingBooking_returnsConfirmed() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        when(bookingRepository.save(entity)).thenReturn(entity);
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.CONFIRMED, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.confirm(1L, 1L);

        assertEquals(BookingStatus.CONFIRMED, result.status());
    }

    @Test
    void confirm_notOwner_throwsForbidden() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        assertThrows(ForbiddenException.class, () -> bookingService.confirm(1L, 3L));
    }

    @Test
    void confirm_notPending_throwsConflict() {
        BookingEntity entity = createEntity(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));

        assertThrows(ConflictException.class, () -> bookingService.confirm(1L, 1L));
    }

    @Test
    void confirm_notFound_throwsNotFoundException() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingService.confirm(1L, 1L));
    }

    @Test
    void cancel_pendingBooking_returnsCancelled() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        when(bookingRepository.save(entity)).thenReturn(entity);
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.CANCELLED, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.cancel(1L, 1L);

        assertEquals(BookingStatus.CANCELLED, result.status());
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    void cancel_completedBooking_throwsConflict() {
        BookingEntity entity = createEntity(BookingStatus.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));

        assertThrows(ConflictException.class, () -> bookingService.cancel(1L, 1L));
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        BookingEntity entity = createEntity(BookingStatus.CANCELLED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));

        assertThrows(ConflictException.class, () -> bookingService.cancel(1L, 1L));
    }

    @Test
    void cancel_notClientOrOwner_throwsForbidden() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        assertThrows(ForbiddenException.class, () -> bookingService.cancel(1L, 3L));
    }

    @Test
    void cancel_notFound_throwsNotFoundException() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingService.cancel(1L, 1L));
    }

    @Test
    void complete_confirmedBooking_returnsCompleted() {
        BookingEntity entity = createEntity(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        when(bookingRepository.save(entity)).thenReturn(entity);
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.COMPLETED, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.complete(1L, 1L);

        assertEquals(BookingStatus.COMPLETED, result.status());
        verify(eventPublisher).publishEvent(any(BookingCompletedEvent.class));
    }

    @Test
    void complete_activeBooking_returnsCompleted() {
        BookingEntity entity = createEntity(BookingStatus.ACTIVE);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        when(bookingRepository.save(entity)).thenReturn(entity);
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.COMPLETED, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        BookingResponse result = bookingService.complete(1L, 1L);

        assertEquals(BookingStatus.COMPLETED, result.status());
    }

    @Test
    void complete_pendingBooking_throwsConflict() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));

        assertThrows(ConflictException.class, () -> bookingService.complete(1L, 1L));
    }

    @Test
    void complete_notOwner_throwsForbidden() {
        BookingEntity entity = createEntity(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        assertThrows(ForbiddenException.class, () -> bookingService.complete(1L, 3L));
    }

    @Test
    void complete_notFound_throwsNotFoundException() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingService.complete(1L, 1L));
    }
}
