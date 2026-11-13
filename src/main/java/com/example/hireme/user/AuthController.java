package com.example.hireme.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register and log in (no token needed)")
@SecurityRequirements // these endpoints don't need a token
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(operationId = "register", summary = "Register a client or owner account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return new ResponseEntity<>(userService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Log in and get a JWT", description = "Send the token as `Authorization: Bearer <token>`.")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "401", description = "Wrong email or password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/demo")
    @Operation(operationId = "getDemoStatus", summary = "Whether the demo owner is available on this server")
    public ResponseEntity<DemoStatusResponse> demoStatus() {
        return ResponseEntity.ok(authService.demoStatus());
    }

    @PostMapping("/demo-owner")
    @Operation(operationId = "loginAsDemoOwner", summary = "Sign in as the demo owner, without a password",
            description = "Only on deployments seeded with demo data (`hireme.demo-listings.enabled=true`), "
                    + "so visitors can try the owner side in one click.")
    @ApiResponse(responseCode = "200", description = "Signed in as the demo owner")
    @ApiResponse(responseCode = "404", description = "This server has no demo data")
    public ResponseEntity<AuthResponse> demoOwnerLogin() {
        return ResponseEntity.ok(authService.demoOwnerLogin());
    }
}
