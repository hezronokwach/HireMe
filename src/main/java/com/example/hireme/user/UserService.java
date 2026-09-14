package com.example.hireme.user;

import com.example.hireme.user.dto.RegisterRequest;
import com.example.hireme.user.dto.UserResponse;

public interface UserService {
    UserResponse register(RegisterRequest registerRequest);
    boolean isOwner(Long userId);
    UserResponse getProfile(Long  userId);
}
