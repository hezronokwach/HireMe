package com.example.hireme.user;

public interface AuthService {
    AuthResponse login(LoginRequest loginRequest);

    DemoStatusResponse demoStatus();

    /** Signs in as the demo owner without a password; 404 when the demo isn't set up. */
    AuthResponse demoOwnerLogin();
}
