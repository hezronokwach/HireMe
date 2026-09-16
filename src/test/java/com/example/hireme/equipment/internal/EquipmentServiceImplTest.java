package com.example.hireme.equipment.internal;

import com.example.hireme.equipment.*;
import com.example.hireme.equipment.internal.exception.EquipmentNotFoundException;
import com.example.hireme.equipment.internal.exception.ForbiddenException;
import com.example.hireme.shared.event.BookingCancelledEvent;
import com.example.hireme.shared.event.BookingCompletedEvent;
import com.example.hireme.shared.event.BookingCreatedEvent;
import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceImplTest {

    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private EquipmentServiceImpl equipmentService;

    private CreateEquipmentRequest createRequest() {
        return new CreateEquipmentRequest("Excavator", Category.HEAVY_MACHINERY, "Big machine",
                BigDecimal.valueOf(1000), "Nairobi");
    }

    private EquipmentEntity createEntity(EquipmentStatus status) {
        EquipmentEntity entity = new EquipmentEntity();
        entity.setId(1L);
        entity.setOwnerId(1L);
        entity.setName("Excavator");
        entity.setCategory(Category.HEAVY_MACHINERY);
        entity.setStatus(status);
        entity.setDailyRateKes(BigDecimal.valueOf(1000));
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private EquipmentResponse equipmentResponse(EquipmentStatus status) {
        return new EquipmentResponse(1L, 1L, "Excavator", Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi", status, Instant.now(), null);
    }

    @Test
    void create_ownerUser_returnsResponse() {
        CreateEquipmentRequest request = createRequest();
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        EquipmentResponse response = equipmentResponse(EquipmentStatus.AVAILABLE);

        when(userService.isOwner(1L)).thenReturn(true);
        when(equipmentMapper.requestToEntity(request, 1L)).thenReturn(entity);
        when(equipmentRepository.save(entity)).thenReturn(entity);
        when(equipmentMapper.toCreateResponse(entity)).thenReturn(response);

        EquipmentResponse result = equipmentService.create(request, 1L);

        assertNotNull(result);
        assertEquals("Excavator", result.name());
    }

    @Test
    void create_nonOwnerUser_throwsForbidden() {
        when(userService.isOwner(1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> equipmentService.create(createRequest(), 1L));
    }

    @Test
    void getById_existingEquipment_returnsResponse() {
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        EquipmentResponse response = equipmentResponse(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(equipmentMapper.toCreateResponse(entity)).thenReturn(response);

        EquipmentResponse result = equipmentService.getById(1L);

        assertEquals("Excavator", result.name());
    }

    @Test
    void getById_notFound_throwsException() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class, () -> equipmentService.getById(1L));
    }

    @Test
    void getAll_noFilter_returnsAll() {
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        EquipmentResponse response = equipmentResponse(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findAll()).thenReturn(List.of(entity));
        when(equipmentMapper.toCreateResponse(entity)).thenReturn(response);

        List<EquipmentResponse> result = equipmentService.getAll(null);

        assertEquals(1, result.size());
    }

    @Test
    void getAll_withCategoryFilter_returnsFilteredList() {
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        EquipmentResponse response = equipmentResponse(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findByCategory(Category.HEAVY_MACHINERY)).thenReturn(List.of(entity));
        when(equipmentMapper.toCreateResponse(entity)).thenReturn(response);

        List<EquipmentResponse> result = equipmentService.getAll(Category.HEAVY_MACHINERY);

        assertEquals(1, result.size());
    }

    @Test
    void update_ownerAndAvailable_returnsUpdated() {
        UpdateEquipmentRequest request = new UpdateEquipmentRequest("New Name", null, null, null, null);
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        EquipmentResponse response = equipmentResponse(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userService.isOwner(1L)).thenReturn(true);
        when(equipmentRepository.save(entity)).thenReturn(entity);
        when(equipmentMapper.toCreateResponse(entity)).thenReturn(response);

        EquipmentResponse result = equipmentService.update(1L, request, 1L);

        assertNotNull(result);
    }

    @Test
    void update_notOwner_throwsForbidden() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.AVAILABLE)));
        when(userService.isOwner(1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> equipmentService.update(1L, new UpdateEquipmentRequest(null, null, null, null, null), 1L));
    }

    @Test
    void update_hiredEquipment_throwsForbidden() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.HIRED)));
        when(userService.isOwner(1L)).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> equipmentService.update(1L, new UpdateEquipmentRequest(null, null, null, null, null), 1L));
    }

    @Test
    void update_notFound_throwsException() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class, () -> equipmentService.update(1L, new UpdateEquipmentRequest(null, null, null, null, null), 1L));
    }

    @Test
    void delete_ownerAndAvailable_deletes() {
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(userService.isOwner(1L)).thenReturn(true);

        equipmentService.delete(1L, 1L);

        verify(equipmentRepository).delete(entity);
    }

    @Test
    void delete_notOwner_throwsForbidden() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.AVAILABLE)));
        when(userService.isOwner(1L)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> equipmentService.delete(1L, 1L));
    }

    @Test
    void delete_hiredEquipment_throwsForbidden() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.HIRED)));
        when(userService.isOwner(1L)).thenReturn(true);

        assertThrows(ForbiddenException.class, () -> equipmentService.delete(1L, 1L));
    }

    @Test
    void delete_notFound_throwsException() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class, () -> equipmentService.delete(1L, 1L));
    }

    @Test
    void isAvailable_available_returnsTrue() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.AVAILABLE)));

        assertTrue(equipmentService.isAvailable(1L));
    }

    @Test
    void isAvailable_hired_returnsFalse() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(createEntity(EquipmentStatus.HIRED)));

        assertFalse(equipmentService.isAvailable(1L));
    }

    @Test
    void isAvailable_notFound_throwsException() {
        when(equipmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EquipmentNotFoundException.class, () -> equipmentService.isAvailable(1L));
    }

    @Test
    void markAsHired_setsStatusAndPublishesEvent() {
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));

        equipmentService.markAsHired(1L);

        assertEquals(EquipmentStatus.HIRED, entity.getStatus());
        verify(equipmentRepository).save(entity);
        verify(applicationEventPublisher).publishEvent(any(EquipmentStatusChangedEvent.class));
    }

    @Test
    void markAsAvailable_setsStatusAndPublishesEvent() {
        EquipmentEntity entity = createEntity(EquipmentStatus.HIRED);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));

        equipmentService.markAsAvailable(1L);

        assertEquals(EquipmentStatus.AVAILABLE, entity.getStatus());
        verify(equipmentRepository).save(entity);
        verify(applicationEventPublisher).publishEvent(any(EquipmentStatusChangedEvent.class));
    }

    @Test
    void onBookingCreatedEvent_marksAsHired() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, 1L, Instant.now());
        EquipmentEntity entity = createEntity(EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));

        equipmentService.on(event);

        assertEquals(EquipmentStatus.HIRED, entity.getStatus());
    }

    @Test
    void onBookingCancelledEvent_marksAsAvailable() {
        BookingCancelledEvent event = new BookingCancelledEvent(1L, 1L, Instant.now());
        EquipmentEntity entity = createEntity(EquipmentStatus.HIRED);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));

        equipmentService.on(event);

        assertEquals(EquipmentStatus.AVAILABLE, entity.getStatus());
    }

    @Test
    void onBookingCompletedEvent_marksAsAvailable() {
        BookingCompletedEvent event = new BookingCompletedEvent(1L, 1L, Instant.now());
        EquipmentEntity entity = createEntity(EquipmentStatus.HIRED);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(entity));

        equipmentService.on(event);

        assertEquals(EquipmentStatus.AVAILABLE, entity.getStatus());
    }
}
