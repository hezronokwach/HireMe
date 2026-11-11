package com.example.hireme.equipment;

import com.example.hireme.shared.web.PageRequests;
import com.example.hireme.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/ai")
@RequiredArgsConstructor
@Tag(name = "AI")
public class EquipmentAiController {

    private final EquipmentDescriptionService descriptionService;
    private final EquipmentAiSearchService aiSearchService;
    private final EquipmentAssistantService assistantService;
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
        return ResponseEntity.ok(descriptionService.describe(request, getUserId(userDetails)));
    }

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "searchEquipmentWithAi", summary = "Search listings with a sentence",
            description = "e.g. \"a tractor in Nakuru next weekend under 8000 a day\". The AI turns it into the filters of "
                    + "GET /api/v1/equipment (returned in `filters`, for the UI to show and edit) and the normal search runs "
                    + "with them. If AI is unavailable or over the limit, the words are matched instead (`interpretedBy: KEYWORDS`).")
    @ApiResponse(responseCode = "200", description = "The interpreted filters and the matching listings")
    public ResponseEntity<AiSearchResponse> search(
            @Valid @RequestBody AiSearchRequest request,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to " + PageRequests.MAX_SIZE)
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size,
            @Parameter(description = "As for GET /api/v1/equipment", example = "dailyRateKes,asc")
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal User userDetails
    ) {
        var pageable = PageRequests.of(page, size, sort, EquipmentController.SORTABLE_FIELDS, EquipmentController.DEFAULT_SORT, "id");
        return ResponseEntity.ok(aiSearchService.search(request, pageable, getUserId(userDetails)));
    }

    @PostMapping("/assistant")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "chatWithAssistant", summary = "Talk with the hire assistant",
            description = "A conversation that finds, compares and prices listings. Send the whole conversation so far "
                    + "(the server keeps nothing); the last message is the user's. The assistant reads the real listings "
                    + "through read-only tools. `listings` are cards built from the database, only for listings it looked "
                    + "up, with the price and availability for the days being discussed. It never books anything.")
    @ApiResponse(responseCode = "200", description = "The assistant's reply, listing cards and suggested follow-ups")
    @ApiResponse(responseCode = "429", description = "Too many AI requests from this user in the last minute")
    @ApiResponse(responseCode = "503", description = "AI is turned off or Groq is unavailable")
    public ResponseEntity<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest request,
            @AuthenticationPrincipal User userDetails
    ) {
        return ResponseEntity.ok(assistantService.chat(request, getUserId(userDetails)));
    }

    private Long getUserId(User userDetails) {
        return userService.getUserIdByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found in security context"));
    }
}
