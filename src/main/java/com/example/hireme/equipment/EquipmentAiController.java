package com.example.hireme.equipment;

import com.example.hireme.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/ai")
@RequiredArgsConstructor
@Tag(name = "AI")
public class EquipmentAiController {

    private final EquipmentDescriptionService descriptionService;
    private final UserService userService;

    @PostMapping("/describe")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(operationId = "describeEquipment", summary = "Draft a listing title and description (OWNER, ADMIN)",
            description = "Also suggests a daily rate from similar listings, with a one-line reason. Nothing is saved: "
                    + "show the draft to the owner, who accepts or edits it before creating the listing.")
    @ApiResponse(responseCode = "200", description = "The draft")
    @ApiResponse(responseCode = "429", description = "Too many AI requests from this user in the last minute")
    @ApiResponse(responseCode = "503", description = "AI is turned off or Groq is unavailable")
    public ResponseEntity<EquipmentDescriptionResponse> describe(
            @Valid @RequestBody DescribeEquipmentRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        Long userId = userService.getUserIdByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
        return ResponseEntity.ok(descriptionService.describe(request, userId));
    }
}
