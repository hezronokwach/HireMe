package com.example.hireme.user;

import com.example.hireme.user.dto.RegisterRequest;
import com.example.hireme.user.dto.UserResponse;

import java.util.Optional;

public interface UserService {
    UserResponse register(RegisterRequest registerRequest);
    boolean isOwner(Long userId);
    UserResponse getProfile(Long userId);
    Optional<Long> getUserIdByEmail(String email);
}
