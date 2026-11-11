package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.*;
import com.example.hireme.equipment.internal.exception.EquipmentInUseException;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentServiceImpl implements EquipmentService {

    static final int MAX_AVAILABILITY_RANGE_DAYS = 366;

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EquipmentBookingChecker equipmentBookingChecker;

    @Override
    @Transactional
    public EquipmentResponse create(CreateEquipmentRequest createEquipmentRequest, Long currentUserId) {
        if (!userService.isOwner(currentUserId)) {
            throw new ForbiddenException();
        }
        EquipmentEntity equipmentEntity = equipmentMapper.requestToEntity(createEquipmentRequest, currentUserId);
        EquipmentEntity equipmentEntitySaved = equipmentRepository.save(equipmentEntity);
        return equipmentMapper.toCreateResponse(equipmentEntitySaved);
    }

    @Override
    public EquipmentResponse getById(Long equipmentId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        return equipmentMapper.toCreateResponse(equipmentEntity);
    }

    @Override
    public PageResponse<EquipmentResponse> search(EquipmentSearchCriteria criteria, Pageable pageable) {
        validateRates(criteria);
        EquipmentSearchCriteria withDates = withValidDates(criteria);
        Set<Long> booked = withDates.hasDates()
                ? equipmentBookingChecker.findBookedEquipmentIds(withDates.availableFrom(), withDates.availableTo())
                : Set.of();
        return PageResponse.from(
                equipmentRepository.findAll(EquipmentSpecifications.matching(withDates, booked), pageable),
                equipmentMapper::toCreateResponse);
    }

    // A single date means that one day. The range may not run backwards or be longer than a year.
    private static EquipmentSearchCriteria withValidDates(EquipmentSearchCriteria c) {
        if (!c.hasDates()) {
            return c;
        }
        LocalDate from = c.availableFrom() != null ? c.availableFrom() : c.availableTo();
        LocalDate to = c.availableTo() != null ? c.availableTo() : c.availableFrom();
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'availableTo' cannot be before 'availableFrom'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_AVAILABILITY_RANGE_DAYS) {
            throw new InvalidRequestException("The availability range cannot be longer than " + MAX_AVAILABILITY_RANGE_DAYS + " days");
        }
        return new EquipmentSearchCriteria(c.category(), c.location(), c.minRate(), c.maxRate(), c.keywords(), from, to);
    }

    private void validateRates(EquipmentSearchCriteria criteria) {
        if (isNegative(criteria.minRate()) || isNegative(criteria.maxRate())) {
            throw new InvalidRequestException("'minRate' and 'maxRate' cannot be negative");
        }
        if (criteria.minRate() != null && criteria.maxRate() != null && criteria.minRate().compareTo(criteria.maxRate()) > 0) {
            throw new InvalidRequestException("'minRate' cannot be greater than 'maxRate'");
        }
    }

    private static boolean isNegative(BigDecimal rate) {
        return rate != null && rate.signum() < 0;
    }

    @Override
    @Transactional
    public EquipmentResponse update(Long equipmentId, UpdateEquipmentRequest updateEquipmentRequest, Long currentUserId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        if (!canManage(equipmentEntity, currentUserId)) {
            throw new ForbiddenException();
        }
        EquipmentStatus previousStatus = equipmentEntity.getStatus();
        equipmentMapper.applyUpdate(equipmentEntity, updateEquipmentRequest);
        EquipmentEntity equipmentEntitySaved = equipmentRepository.save(equipmentEntity);
        if (equipmentEntitySaved.getStatus() != previousStatus) {
            applicationEventPublisher.publishEvent(
                    new EquipmentStatusChangedEvent(
                            equipmentId,
                            equipmentEntitySaved.getStatus(),
                            Instant.now())
            );
        }
        return equipmentMapper.toCreateResponse(equipmentEntitySaved);
    }

    @Override
    @Transactional
    public void delete(Long equipmentId, Long currentUserId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        if (!canManage(equipmentEntity, currentUserId)) {
            throw new ForbiddenException();
        }
        if (equipmentBookingChecker.hasOpenBookings(equipmentId)) {
            throw new EquipmentInUseException();
        }
        equipmentRepository.delete(equipmentEntity);
    }

    private boolean canManage(EquipmentEntity equipmentEntity, Long currentUserId) {
        return Objects.equals(equipmentEntity.getOwnerId(), currentUserId) || userService.isAdmin(currentUserId);
    }

    @Override
    public PageResponse<EquipmentResponse> getByOwner(Long ownerId, Pageable pageable) {
        return PageResponse.from(equipmentRepository.findByOwnerId(ownerId, pageable), equipmentMapper::toCreateResponse);
    }

    @Override
    public List<Long> getIdsByOwner(Long ownerId) {
        return equipmentRepository.findIdsByOwnerId(ownerId);
    }

    @Override
    public boolean isAvailable(Long equipmentId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        return equipmentEntity.getStatus() == EquipmentStatus.AVAILABLE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForBooking(Long equipmentId) {
        equipmentRepository.findByIdForUpdate(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
    }
}
