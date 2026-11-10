package com.example.hireme.equipment;

import com.example.hireme.shared.exception.InvalidRequestException;
import com.example.hireme.shared.web.PageResponse;
import com.example.hireme.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentControllerTest {

    @Mock
    private EquipmentService equipmentService;
    @Mock
    private UserService userService;

    @InjectMocks
    private EquipmentController equipmentController;

    private User userDetails() {
        return new User("owner@test.com", "password", List.of());
    }

    private EquipmentResponse equipmentResponse() {
        return new EquipmentResponse(1L, 1L, "Excavator", Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi", EquipmentStatus.AVAILABLE, Instant.now(), null);
    }

    @Test
    void create_validRequest_returnsCreated() {
        CreateEquipmentRequest request = new CreateEquipmentRequest("Excavator", Category.HEAVY_MACHINERY,
                "Big machine", BigDecimal.valueOf(1000), "Nairobi");
        when(userService.getUserIdByEmail("owner@test.com")).thenReturn(Optional.of(1L));
        when(equipmentService.create(request, 1L)).thenReturn(equipmentResponse());

        ResponseEntity<EquipmentResponse> result = equipmentController.create(request, userDetails());

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("Excavator", result.getBody().name());
    }

    @Test
    void getById_existingEquipment_returnsOk() {
        when(equipmentService.getById(1L)).thenReturn(equipmentResponse());

        ResponseEntity<EquipmentResponse> result = equipmentController.getById(1L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void search_defaults_newestFirstWithIdTieBreaker() {
        PageResponse<EquipmentResponse> page = new PageResponse<>(List.of(equipmentResponse()), 0, 20, 1, 1);
        Pageable expected = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        when(equipmentService.search(EquipmentSearchCriteria.none(), expected)).thenReturn(page);

        ResponseEntity<PageResponse<EquipmentResponse>> result =
                equipmentController.search(null, null, null, null, null, null, null, 0, 20, null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(page, result.getBody());
    }

    @Test
    void search_passesFiltersPageAndSort() {
        EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(Category.AGRICULTURAL, "nakuru",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(8000));
        Pageable expected = PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "dailyRateKes").and(Sort.by("id")));
        when(equipmentService.search(criteria, expected)).thenReturn(new PageResponse<>(List.of(), 2, 10, 0, 0));

        equipmentController.search(Category.AGRICULTURAL, "nakuru", BigDecimal.valueOf(1000), BigDecimal.valueOf(8000), null, null, null,
                2, 10, "dailyRateKes,asc");

        verify(equipmentService).search(criteria, expected);
    }

    @Test
    void search_unknownSortField_throwsInvalidRequest() {
        assertThrows(InvalidRequestException.class,
                () -> equipmentController.search(null, null, null, null, null, null, null, 0, 20, "ownerId"));
        verifyNoInteractions(equipmentService);
    }

    @Test
    void search_sizeTooLarge_throwsInvalidRequest() {
        assertThrows(InvalidRequestException.class,
                () -> equipmentController.search(null, null, null, null, null, null, null, 0, 101, null));
    }

    @Test
    void update_validRequest_returnsOk() {
        UpdateEquipmentRequest request = new UpdateEquipmentRequest("New Name", null, null, null, null, null);
        when(userService.getUserIdByEmail("owner@test.com")).thenReturn(Optional.of(1L));
        when(equipmentService.update(1L, request, 1L)).thenReturn(equipmentResponse());

        ResponseEntity<EquipmentResponse> result = equipmentController.update(1L, request, userDetails());

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void delete_existingEquipment_returnsNoContent() {
        when(userService.getUserIdByEmail("owner@test.com")).thenReturn(Optional.of(1L));

        ResponseEntity<Void> result = equipmentController.delete(1L, userDetails());

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(equipmentService).delete(1L, 1L);
    }

    @Test
    void getUserId_userNotFound_throwsRuntimeException() {
        when(userService.getUserIdByEmail("owner@test.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> equipmentController.create(
                new CreateEquipmentRequest("Excavator", Category.HEAVY_MACHINERY,
                        "Big machine", BigDecimal.valueOf(1000), "Nairobi"),
                userDetails()));
    }
}
