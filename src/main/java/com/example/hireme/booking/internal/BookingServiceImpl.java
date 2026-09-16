package com.example.hireme.booking.internal;

import com.example.hireme.booking.*;
import com.example.hireme.booking.exception.ConflictException;
import com.example.hireme.booking.exception.ForbiddenException;
import com.example.hireme.booking.exception.NotFoundException;
import com.example.hireme.booking.exception.ValidationException;
import com.example.hireme.equipment.EquipmentResponse;
import com.example.hireme.equipment.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    private final EquipmentService equipmentService;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest createBookingRequest, Long currentClientId) {
        if (!equipmentService.isAvailable(createBookingRequest.equipmentId())) {
            throw new ConflictException("Equipment is not available");
        }
        if (createBookingRequest.endDate().isBefore(createBookingRequest.startDate())) {
            throw new ValidationException("Start date cannot be after end date");
        }
        EquipmentResponse equipment = equipmentService.getById(createBookingRequest.equipmentId());
        long numberOfDays = ChronoUnit.DAYS.between(createBookingRequest.startDate(), createBookingRequest.endDate()) + 1;
        BigDecimal totalCostKes = equipment.dailyRateKes().multiply(BigDecimal.valueOf(numberOfDays));

        BookingEntity bookingEntity = bookingMapper.toBookingEntity(createBookingRequest, currentClientId, totalCostKes);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);

        equipmentService.markAsHired(savedBooking.getEquipmentId());
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
        bookingEntity.setStatus(BookingStatus.CONFIRMED);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);
        return bookingMapper.toBookingResponse(savedBooking);
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

        equipmentService.markAsAvailable(savedBooking.getEquipmentId());
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

        equipmentService.markAsAvailable(savedBooking.getEquipmentId());
        eventPublisher.publishEvent(new BookingCompletedEvent(savedBooking.getBookingId(), savedBooking.getEquipmentId(), Instant.now()));

        return bookingMapper.toBookingResponse(savedBooking);
    }
}
