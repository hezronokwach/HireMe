package com.example.hireme.user;

import java.util.Optional;

public interface UserService {
    UserResponse register(RegisterRequest registerRequest);
    boolean isOwner(Long userId);
    UserResponse getProfile(Long userId);
    Optional<Long> getUserIdByEmail(String email);
}
