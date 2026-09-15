package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.EquipmentService;
import com.example.hireme.equipment.dto.CreateEquipmentRequest;
import com.example.hireme.equipment.dto.EquipmentResponse;
import com.example.hireme.equipment.dto.UpdateEquipmentRequest;
import com.example.hireme.equipment.events.EquipmentStatusChangedEvent;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;
    private final UserService userService;
    private final ApplicationEventPublisher  applicationEventPublisher;

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
    public List<EquipmentResponse> getAll(EquipmentEntity.Category categoryFilter) {
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
        if(!userService.isOwner(currentUserId)) {
            throw new ForbiddenException();
        }
        if(equipmentEntity.status == EquipmentEntity.EquipmentStatus.HIRED) {
            throw new ForbiddenException();
        }
        equipmentMapper.applyUpdate(equipmentEntity, updateEquipmentRequest);
        EquipmentEntity equipmentEntitySaved = equipmentRepository.save(equipmentEntity);
        return equipmentMapper.toCreateResponse(equipmentEntitySaved);
    }

    @Override
    @Transactional
    public void delete(Long equipmentId, Long currentUserId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        if(!userService.isOwner(currentUserId)) {
            throw new ForbiddenException();
        }
        if(equipmentEntity.status == EquipmentEntity.EquipmentStatus.HIRED) {
            throw new ForbiddenException();
        }
        equipmentRepository.delete(equipmentEntity);
    }

    @Override
    public boolean isAvailable(Long equipmentId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        return equipmentEntity.status == EquipmentEntity.EquipmentStatus.AVAILABLE;
    }

    @Override
    @Transactional
    public void markAsHired(Long equipmentId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        equipmentEntity.setStatus(EquipmentEntity.EquipmentStatus.HIRED);
        equipmentRepository.save(equipmentEntity);
        applicationEventPublisher.publishEvent(
                new EquipmentStatusChangedEvent(
                        equipmentId,
                        EquipmentEntity.EquipmentStatus.HIRED,
                        Instant.now())
        );    }

    @Override
    @Transactional
    public void markAsAvailable(Long equipmentId) {
        EquipmentEntity equipmentEntity = equipmentRepository.findById(equipmentId)
                .orElseThrow(EquipmentNotFoundException::new);
        equipmentEntity.setStatus(EquipmentEntity.EquipmentStatus.AVAILABLE);
        equipmentRepository.save(equipmentEntity);
        applicationEventPublisher.publishEvent(
                new EquipmentStatusChangedEvent(
                        equipmentId,
                        EquipmentEntity.EquipmentStatus.AVAILABLE,
                        Instant.now())
        );

    }
}
