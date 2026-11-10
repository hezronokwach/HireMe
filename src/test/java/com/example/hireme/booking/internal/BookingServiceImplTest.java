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
import com.example.hireme.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
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

    private static final Pageable PAGE = PageRequest.of(0, 20);

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

        assertThrows(ValidationException.class, () -> bookingService.createBooking(request, 1L));
        verifyNoInteractions(equipmentService, bookingRepository);
    }

    @Test
    void createBooking_datesAlreadyBooked_throwsConflict() {
        CreateBookingRequest request = createRequest();
        when(equipmentService.isAvailable(1L)).thenReturn(true);
        when(bookingRepository.findOverlapping(1L, request.startDate(), request.endDate(), BookingServiceImpl.BLOCKING_STATUSES))
                .thenReturn(List.of(createEntity(BookingStatus.CONFIRMED)));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(request, 1L));
        assertEquals("Equipment is already booked for some of these dates", ex.getMessage());
        verify(bookingRepository, never()).save(any());
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
    void getMyBookings_returnsPage() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findByClientId(1L, PAGE)).thenReturn(new PageImpl<>(List.of(entity), PAGE, 1));
        BookingResponse response = new BookingResponse(1L, 1L, 1L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        PageResponse<BookingResponse> result = bookingService.getMyBookings(1L, PAGE);

        assertEquals(new PageResponse<>(List.of(response), 0, 20, 1, 1), result);
    }

    @Test
    void getBookingsForMyEquipment_noStatus_returnsAllBookingsOnOwnersEquipment() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(equipmentService.getIdsByOwner(1L)).thenReturn(List.of(1L, 2L));
        when(bookingRepository.findByEquipmentIdIn(List.of(1L, 2L), PAGE)).thenReturn(new PageImpl<>(List.of(entity), PAGE, 1));
        BookingResponse response = new BookingResponse(1L, 1L, 2L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        PageResponse<BookingResponse> result = bookingService.getBookingsForMyEquipment(1L, null, PAGE);

        assertEquals(List.of(response), result.content());
        verify(bookingRepository, never()).findByEquipmentIdInAndStatus(any(), any(), any());
    }

    @Test
    void getBookingsForMyEquipment_withStatus_filtersByStatus() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(equipmentService.getIdsByOwner(1L)).thenReturn(List.of(1L));
        when(bookingRepository.findByEquipmentIdInAndStatus(List.of(1L), BookingStatus.PENDING, PAGE))
                .thenReturn(new PageImpl<>(List.of(entity), PAGE, 1));
        BookingResponse response = new BookingResponse(1L, 1L, 2L, null, null, null, BookingStatus.PENDING, null);
        when(bookingMapper.toBookingResponse(entity)).thenReturn(response);

        PageResponse<BookingResponse> result = bookingService.getBookingsForMyEquipment(1L, BookingStatus.PENDING, PAGE);

        assertEquals(List.of(response), result.content());
        verify(bookingRepository, never()).findByEquipmentIdIn(any(), any());
    }

    @Test
    void getBookingsForMyEquipment_ownerHasNoEquipment_returnsEmptyWithoutQuery() {
        when(equipmentService.getIdsByOwner(1L)).thenReturn(List.of());

        PageResponse<BookingResponse> result = bookingService.getBookingsForMyEquipment(1L, null, PAGE);

        assertEquals(new PageResponse<BookingResponse>(List.of(), 0, 20, 0, 0), result);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void getAvailability_noBookings_isAvailable() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        when(bookingRepository.findOverlapping(1L, from, to, BookingServiceImpl.BLOCKING_STATUSES)).thenReturn(List.of());

        EquipmentAvailabilityResponse result = bookingService.getAvailability(1L, from, to);

        assertTrue(result.available());
        assertTrue(result.bookedPeriods().isEmpty());
        assertEquals(from, result.from());
        assertEquals(to, result.to());
    }

    @Test
    void getAvailability_withConfirmedBooking_listsBookedPeriod() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        LocalDate to = LocalDate.of(2030, 1, 31);
        BookingEntity confirmed = createEntity(BookingStatus.CONFIRMED);
        confirmed.setStartDate(LocalDate.of(2030, 1, 10));
        confirmed.setEndDate(LocalDate.of(2030, 1, 12));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        when(bookingRepository.findOverlapping(1L, from, to, BookingServiceImpl.BLOCKING_STATUSES)).thenReturn(List.of(confirmed));

        EquipmentAvailabilityResponse result = bookingService.getAvailability(1L, from, to);

        assertFalse(result.available());
        assertEquals(List.of(new EquipmentAvailabilityResponse.BookedPeriod(LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12))),
                result.bookedPeriods());
    }

    @Test
    void getAvailability_underMaintenance_isNotAvailable() {
        LocalDate from = LocalDate.of(2030, 1, 1);
        EquipmentResponse maintenance = new EquipmentResponse(1L, 2L, "Excavator", com.example.hireme.equipment.Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi", EquipmentStatus.UNDER_MAINTENANCE, Instant.now(), null);
        when(equipmentService.getById(1L)).thenReturn(maintenance);
        when(bookingRepository.findOverlapping(1L, from, from, BookingServiceImpl.BLOCKING_STATUSES)).thenReturn(List.of());

        assertFalse(bookingService.getAvailability(1L, from, from).available());
    }

    @Test
    void getAvailability_toBeforeFrom_throwsValidation() {
        assertThrows(ValidationException.class,
                () -> bookingService.getAvailability(1L, LocalDate.of(2030, 1, 5), LocalDate.of(2030, 1, 4)));
        verifyNoInteractions(equipmentService, bookingRepository);
    }

    @Test
    void getAvailability_rangeTooLong_throwsValidation() {
        LocalDate from = LocalDate.of(2030, 1, 1);

        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        // 366 days including both ends is allowed; one more day is not
        assertDoesNotThrow(() -> bookingService.getAvailability(1L, from, from.plusDays(BookingServiceImpl.MAX_AVAILABILITY_RANGE_DAYS - 1L)));
        assertThrows(ValidationException.class,
                () -> bookingService.getAvailability(1L, from, from.plusDays(BookingServiceImpl.MAX_AVAILABILITY_RANGE_DAYS)));
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
    void confirm_datesAlreadyConfirmed_throwsConflictAfterLocking() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        BookingEntity confirmed = createEntity(BookingStatus.CONFIRMED);
        confirmed.setBookingId(2L);
        when(bookingRepository.findOverlapping(1L, entity.getStartDate(), entity.getEndDate(), BookingServiceImpl.BLOCKING_STATUSES))
                .thenReturn(List.of(confirmed));

        assertThrows(ConflictException.class, () -> bookingService.confirm(1L, 1L));

        InOrder inOrder = inOrder(equipmentService, bookingRepository);
        inOrder.verify(equipmentService).lockForBooking(1L);
        inOrder.verify(bookingRepository).findOverlapping(1L, entity.getStartDate(), entity.getEndDate(), BookingServiceImpl.BLOCKING_STATUSES);
        assertEquals(BookingStatus.PENDING, entity.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirm_cancelsOverlappingPendingBookings() {
        BookingEntity entity = createEntity(BookingStatus.PENDING);
        BookingEntity otherRequest = createEntity(BookingStatus.PENDING);
        otherRequest.setBookingId(2L);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(1L));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findOverlapping(1L, entity.getStartDate(), entity.getEndDate(), BookingServiceImpl.BLOCKING_STATUSES))
                .thenReturn(List.of());
        when(bookingRepository.findOverlapping(1L, entity.getStartDate(), entity.getEndDate(), EnumSet.of(BookingStatus.PENDING)))
                .thenReturn(List.of(otherRequest));

        bookingService.confirm(1L, 1L);

        assertEquals(BookingStatus.CONFIRMED, entity.getStatus());
        assertEquals(BookingStatus.CANCELLED, otherRequest.getStatus());
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        BookingCancelledEvent cancelled = assertInstanceOf(BookingCancelledEvent.class, event.getValue());
        assertEquals(2L, cancelled.bookingId());
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
    void cancel_activeBookingByClient_throwsForbidden() {
        BookingEntity entity = createEntity(BookingStatus.ACTIVE); // client 1
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () -> bookingService.cancel(1L, 1L));
        assertEquals("Only the equipment owner can cancel an active booking", ex.getMessage());
        assertEquals(BookingStatus.ACTIVE, entity.getStatus());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancel_activeBookingByOwner_returnsCancelled() {
        BookingEntity entity = createEntity(BookingStatus.ACTIVE);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse(2L));
        when(bookingRepository.save(entity)).thenReturn(entity);

        bookingService.cancel(1L, 2L);

        assertEquals(BookingStatus.CANCELLED, entity.getStatus());
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
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
