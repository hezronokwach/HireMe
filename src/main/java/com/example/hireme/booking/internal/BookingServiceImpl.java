package com.example.hireme.booking.internal;

import com.example.hireme.booking.*;
import com.example.hireme.booking.exception.ConflictException;
import com.example.hireme.booking.exception.ForbiddenException;
import com.example.hireme.booking.exception.NotFoundException;
import com.example.hireme.booking.exception.ValidationException;
import com.example.hireme.shared.event.BookingCancelledEvent;
import com.example.hireme.shared.event.BookingCompletedEvent;
import com.example.hireme.shared.event.BookingCreatedEvent;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    // Bookings that hold their dates. PENDING ones don't: several clients can ask for the same dates.
    static final Set<BookingStatus> BLOCKING_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final EquipmentService equipmentService;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest createBookingRequest, Long currentClientId) {
        if (createBookingRequest.endDate().isBefore(createBookingRequest.startDate())) {
            throw new ValidationException("Start date cannot be after end date");
        }
        if (!equipmentService.isAvailable(createBookingRequest.equipmentId())) {
            throw new ConflictException("Equipment is not available for booking");
        }
        if (!bookingRepository.findOverlapping(createBookingRequest.equipmentId(), createBookingRequest.startDate(),
                createBookingRequest.endDate(), BLOCKING_STATUSES).isEmpty()) {
            throw new ConflictException("Equipment is already booked for some of these dates");
        }
        EquipmentResponse equipment = equipmentService.getById(createBookingRequest.equipmentId());
        long numberOfDays = ChronoUnit.DAYS.between(createBookingRequest.startDate(), createBookingRequest.endDate()) + 1;
        BigDecimal totalCostKes = equipment.dailyRateKes().multiply(BigDecimal.valueOf(numberOfDays));

        BookingEntity bookingEntity = bookingMapper.toBookingEntity(createBookingRequest, currentClientId, totalCostKes);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);

        eventPublisher.publishEvent(new BookingCreatedEvent(savedBooking.getBookingId(), savedBooking.getEquipmentId(), Instant.now()));

        return bookingMapper.toBookingResponse(savedBooking);
    }

    @Override
    public BookingResponse getById(Long bookingId, Long currentUserId) {
        BookingEntity bookingEntity = bookingRepository.findById(bookingId)
                .orElseThrow(NotFoundException::new);
        EquipmentResponse equipment = equipmentService.getById(bookingEntity.getEquipmentId());
        if (!Objects.equals(bookingEntity.getClientId(), currentUserId) && !Objects.equals(equipment.ownerId(), currentUserId)) {
            throw new ForbiddenException();
        }
        return bookingMapper.toBookingResponse(bookingEntity);
    }

    @Override
    public List<BookingResponse> getMyBookings(Long currentUserId) {
        return bookingRepository.findByClientId(currentUserId)
                .stream()
                .map(bookingMapper::toBookingResponse)
                .toList();
    }

    @Override
    public List<BookingResponse> getBookingsForMyEquipment(Long currentOwnerId, BookingStatus status) {
        List<Long> equipmentIds = equipmentService.getIdsByOwner(currentOwnerId);
        if (equipmentIds.isEmpty()) {
            return List.of();
        }
        List<BookingEntity> bookings = status == null
                ? bookingRepository.findByEquipmentIdIn(equipmentIds)
                : bookingRepository.findByEquipmentIdInAndStatus(equipmentIds, status);
        return bookings.stream()
                .map(bookingMapper::toBookingResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse confirm(Long bookingId, Long currentOwnerId) {
        BookingEntity bookingEntity = bookingRepository.findById(bookingId)
                .orElseThrow(NotFoundException::new);
        EquipmentResponse equipment = equipmentService.getById(bookingEntity.getEquipmentId());
        if (!Objects.equals(equipment.ownerId(), currentOwnerId)) {
            throw new ForbiddenException();
        }
        if (bookingEntity.getStatus() != BookingStatus.PENDING) {
            throw new ConflictException("Only PENDING booking can be confirmed");
        }
        // Confirmations for the same equipment wait for each other here, so two overlapping
        // bookings can't both pass the check below.
        equipmentService.lockForBooking(bookingEntity.getEquipmentId());
        if (!bookingRepository.findOverlapping(bookingEntity.getEquipmentId(), bookingEntity.getStartDate(),
                bookingEntity.getEndDate(), BLOCKING_STATUSES).isEmpty()) {
            throw new ConflictException("Equipment is already booked for some of these dates");
        }
        bookingEntity.setStatus(BookingStatus.CONFIRMED);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);
        cancelOverlappingPendingBookings(savedBooking);
        return bookingMapper.toBookingResponse(savedBooking);
    }

    // Once dates are confirmed, overlapping requests can never be confirmed, so close them now.
    private void cancelOverlappingPendingBookings(BookingEntity confirmedBooking) {
        bookingRepository.findOverlapping(confirmedBooking.getEquipmentId(), confirmedBooking.getStartDate(),
                        confirmedBooking.getEndDate(), EnumSet.of(BookingStatus.PENDING))
                .forEach(pending -> {
                    pending.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(pending);
                    eventPublisher.publishEvent(new BookingCancelledEvent(pending.getBookingId(), pending.getEquipmentId(), Instant.now()));
                });
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long bookingId, Long currentUserId) {
        BookingEntity bookingEntity = bookingRepository.findById(bookingId)
                .orElseThrow(NotFoundException::new);
        EquipmentResponse equipment = equipmentService.getById(bookingEntity.getEquipmentId());
        boolean isClient = Objects.equals(bookingEntity.getClientId(), currentUserId);
        boolean isOwner = Objects.equals(equipment.ownerId(), currentUserId);
        if (!isClient && !isOwner) {
            throw new ForbiddenException();
        }
        if (bookingEntity.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("Completed Booking cannot be cancelled");
        }
        if (bookingEntity.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking is already cancelled");
        }
        bookingEntity.setStatus(BookingStatus.CANCELLED);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);

        eventPublisher.publishEvent(new BookingCancelledEvent(savedBooking.getBookingId(), savedBooking.getEquipmentId(), Instant.now()));

        return bookingMapper.toBookingResponse(savedBooking);
    }

    @Override
    @Transactional
    public BookingResponse complete(Long bookingId, Long currentOwnerId) {
        BookingEntity bookingEntity = bookingRepository.findById(bookingId)
                .orElseThrow(NotFoundException::new);
        EquipmentResponse equipment = equipmentService.getById(bookingEntity.getEquipmentId());
        if (!Objects.equals(equipment.ownerId(), currentOwnerId)) {
            throw new ForbiddenException();
        }
        if (bookingEntity.getStatus() != BookingStatus.ACTIVE
                && bookingEntity.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Only ACTIVE or CONFIRMED bookings can be completed (current status: " + bookingEntity.getStatus() + ")");
        }
        bookingEntity.setStatus(BookingStatus.COMPLETED);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);

        eventPublisher.publishEvent(new BookingCompletedEvent(savedBooking.getBookingId(), savedBooking.getEquipmentId(), Instant.now()));

        return bookingMapper.toBookingResponse(savedBooking);
    }
}
