package com.example.hireme.equipment;

import com.example.hireme.equipment.dto.CreateEquipmentRequest;
import com.example.hireme.equipment.dto.EquipmentResponse;
import com.example.hireme.equipment.dto.UpdateEquipmentRequest;
import com.example.hireme.equipment.internal.EquipmentEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final com.example.hireme.user.internal.UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<EquipmentResponse> create(
            @Valid @RequestBody CreateEquipmentRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return new ResponseEntity<>(equipmentService.create(request, userId), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<EquipmentResponse>> getAll(
            @RequestParam(required = false) EquipmentEntity.Category category
    ) {
        return ResponseEntity.ok(equipmentService.getAll(category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<EquipmentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEquipmentRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(equipmentService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        equipmentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(User userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(com.example.hireme.user.internal.User::getId)
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
    }
}
