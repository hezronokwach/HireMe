package com.example.hireme.equipment;

import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    static final Set<String> SORTABLE_FIELDS = Set.of("dailyRateKes", "createdAt", "name");
    static final String DEFAULT_SORT = "createdAt,desc"; // newest listings first

    private final EquipmentService equipmentService;
    private final com.example.hireme.user.UserService userService;

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
    public ResponseEntity<PageResponse<EquipmentResponse>> search(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) BigDecimal minRate,
            @RequestParam(required = false) BigDecimal maxRate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort
    ) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT, "id");
        EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(category, location, minRate, maxRate);
        return ResponseEntity.ok(equipmentService.search(criteria, pageable));
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
        return userService.getUserIdByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
    }
}
