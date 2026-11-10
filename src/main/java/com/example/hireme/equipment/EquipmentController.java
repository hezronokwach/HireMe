package com.example.hireme.equipment;

import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Equipment", description = "Equipment listings")
public class EquipmentController {

    static final Set<String> SORTABLE_FIELDS = Set.of("dailyRateKes", "createdAt", "name");
    static final String DEFAULT_SORT = "createdAt,desc"; // newest listings first

    private final EquipmentService equipmentService;
    private final com.example.hireme.user.UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "createEquipment", summary = "Create a listing (OWNER, ADMIN)", description = "The caller becomes its owner.")
    @ApiResponse(responseCode = "201", description = "Listing created")
    public ResponseEntity<EquipmentResponse> create(
            @Valid @RequestBody CreateEquipmentRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = getUserId(userDetails);
        return new ResponseEntity<>(equipmentService.create(request, userId), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getEquipment", summary = "Get one listing")
    @ApiResponse(responseCode = "200", description = "The listing")
    @ApiResponse(responseCode = "404", description = "No such equipment")
    public ResponseEntity<EquipmentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getById(id));
    }

    @GetMapping
    @Operation(operationId = "searchEquipment", summary = "Search listings, one page at a time",
            description = "All filters are optional and combine with AND.")
    public ResponseEntity<PageResponse<EquipmentResponse>> search(
            @RequestParam(required = false) Category category,
            @Parameter(description = "Case-insensitive \"contains\", e.g. `nakuru` matches `Nakuru Town`")
            @RequestParam(required = false) String location,
            @Parameter(description = "Lowest daily rate in KES, included")
            @RequestParam(required = false) BigDecimal minRate,
            @Parameter(description = "Highest daily rate in KES, included")
            @RequestParam(required = false) BigDecimal maxRate,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to " + PageRequests.MAX_SIZE)
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "`dailyRateKes`, `createdAt` or `name`, optionally followed by `,asc` or `,desc`. "
                    + "Default `" + DEFAULT_SORT + "` (newest first).", example = "dailyRateKes,asc")
            @RequestParam(required = false) String sort
    ) {
        Pageable pageable = PageRequests.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT, "id");
        EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(category, location, minRate, maxRate);
        return ResponseEntity.ok(equipmentService.search(criteria, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "updateEquipment", summary = "Update a listing you own (ADMIN: any)",
            description = "Only the fields you send are changed. Send `\"status\": \"UNDER_MAINTENANCE\"` to stop new bookings.")
    @ApiResponse(responseCode = "200", description = "The updated listing")
    @ApiResponse(responseCode = "404", description = "No such equipment")
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
    @Operation(operationId = "deleteEquipment", summary = "Delete a listing you own (ADMIN: any)")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such equipment")
    @ApiResponse(responseCode = "409", description = "It has pending, confirmed or active bookings that end today or later")
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
