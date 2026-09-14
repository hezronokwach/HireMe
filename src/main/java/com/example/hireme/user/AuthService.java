package com.example.hireme.user;

import com.example.hireme.user.dto.AuthResponse;
import com.example.hireme.user.dto.LoginRequest;

public interface AuthService {
    AuthResponse login(LoginRequest loginRequest);
}
