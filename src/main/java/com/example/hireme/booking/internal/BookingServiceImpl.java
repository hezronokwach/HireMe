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
import com.example.hireme.equipment.EquipmentStatus;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.UserContact;
import com.example.hireme.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    // Bookings that hold their dates. PENDING ones don't: several clients can ask for the same dates.
    static final Set<BookingStatus> BLOCKING_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE);
    static final int MAX_AVAILABILITY_RANGE_DAYS = 366;
    // Bookings whose client the owner may still need to call. Once a booking is over, the number is hidden.
    static final Set<BookingStatus> CONTACT_STATUSES =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final EquipmentService equipmentService;
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;

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
        boolean isOwner = Objects.equals(equipment.ownerId(), currentUserId);
        boolean isClient = Objects.equals(bookingEntity.getClientId(), currentUserId);
        boolean isAdmin = !isOwner && !isClient && userService.isAdmin(currentUserId);
        if (!isOwner && !isClient && !isAdmin) {
            throw new ForbiddenException();
        }
        BookingResponse response = bookingMapper.toBookingResponse(bookingEntity);
        return isOwner || isAdmin ? withClientContact(response) : response;
    }

    @Override
    public PageResponse<BookingResponse> getMyBookings(Long currentUserId, Pageable pageable) {
        return PageResponse.from(bookingRepository.findByClientId(currentUserId, pageable), bookingMapper::toBookingResponse);
    }

    @Override
    public PageResponse<BookingResponse> getBookingsForMyEquipment(Long currentOwnerId, BookingStatus status, Pageable pageable) {
        List<Long> equipmentIds = equipmentService.getIdsByOwner(currentOwnerId);
        if (equipmentIds.isEmpty()) {
            return PageResponse.from(Page.<BookingEntity>empty(pageable), bookingMapper::toBookingResponse);
        }
        Page<BookingEntity> bookings = status == null
                ? bookingRepository.findByEquipmentIdIn(equipmentIds, pageable)
                : bookingRepository.findByEquipmentIdInAndStatus(equipmentIds, status, pageable);
        // One lookup for the whole page, not one per booking.
        Map<Long, UserContact> clients = userService.getContacts(
                bookings.getContent().stream().map(BookingEntity::getClientId).distinct().toList());
        return PageResponse.from(bookings, booking ->
                withClientContact(bookingMapper.toBookingResponse(booking), clients.get(booking.getClientId())));
    }

    @Override
    public EquipmentAvailabilityResponse getAvailability(Long equipmentId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ValidationException("'to' cannot be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_AVAILABILITY_RANGE_DAYS) {
            throw new ValidationException("Date range cannot be longer than " + MAX_AVAILABILITY_RANGE_DAYS + " days");
        }
        EquipmentResponse equipment = equipmentService.getById(equipmentId);
        List<EquipmentAvailabilityResponse.BookedPeriod> bookedPeriods = bookingRepository
                .findOverlapping(equipmentId, from, to, BLOCKING_STATUSES)
                .stream()
                .map(booking -> new EquipmentAvailabilityResponse.BookedPeriod(booking.getStartDate(), booking.getEndDate()))
                .toList();
        boolean available = equipment.equipmentStatus() == EquipmentStatus.AVAILABLE && bookedPeriods.isEmpty();
        return new EquipmentAvailabilityResponse(equipmentId, from, to, available, bookedPeriods);
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
        return withClientContact(bookingMapper.toBookingResponse(savedBooking));
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
        // Once the hire has started the client has the equipment, so only the owner can end it early.
        if (bookingEntity.getStatus() == BookingStatus.ACTIVE && !isOwner) {
            throw new ForbiddenException("Only the equipment owner can cancel an active booking");
        }
        bookingEntity.setStatus(BookingStatus.CANCELLED);
        BookingEntity savedBooking = bookingRepository.save(bookingEntity);

        eventPublisher.publishEvent(new BookingCancelledEvent(savedBooking.getBookingId(), savedBooking.getEquipmentId(), Instant.now()));

        BookingResponse response = bookingMapper.toBookingResponse(savedBooking);
        return isOwner ? withClientContact(response) : response;
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

        return withClientContact(bookingMapper.toBookingResponse(savedBooking));
    }

    // For the equipment's owner or an admin: who booked, and their phone while the booking is still going.
    private BookingResponse withClientContact(BookingResponse response) {
        if (response == null || response.clientId() == null) {
            return response;
        }
        return withClientContact(response, userService.getContacts(List.of(response.clientId())).get(response.clientId()));
    }

    private static BookingResponse withClientContact(BookingResponse response, UserContact client) {
        if (client == null) {
            return response;
        }
        return response.withClient(client.fullName(),
                CONTACT_STATUSES.contains(response.status()) ? client.phoneNumber() : null);
    }
}
