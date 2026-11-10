package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.*;
import com.example.hireme.equipment.internal.exception.EquipmentInUseException;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentServiceImpl implements EquipmentService {

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
    public List<EquipmentResponse> getAll(Category categoryFilter) {
        List<EquipmentEntity> equipment;
        if (categoryFilter != null) {
            equipment = equipmentRepository.findByCategory(categoryFilter);
        } else {
            equipment = equipmentRepository.findAll();
        }
        return equipment.stream()
                .map(equipmentMapper::toCreateResponse)
                .toList();
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
