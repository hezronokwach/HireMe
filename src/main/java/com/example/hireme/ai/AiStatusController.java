package com.example.hireme.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI features (Groq)")
public class AiStatusController {

    private final AiAssistant aiAssistant;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getAiStatus", summary = "Whether AI features are on",
            description = "Lets the frontend hide AI buttons when they would only return 503.")
    public ResponseEntity<AiStatusResponse> status() {
        return ResponseEntity.ok(aiAssistant.status());
    }
}
