package com.example.hireme.user.internal;

import com.example.hireme.user.UserService;
import com.example.hireme.user.dto.RegisterRequest;
import com.example.hireme.user.dto.UserResponse;
import com.example.hireme.user.internal.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse register(RegisterRequest registerRequest) {
       if(userRepository.existsByEmail(registerRequest.email())) {
           throw new UserAlreadyExistsException(registerRequest.email());
       }
       String encodedPassword = passwordEncoder.encode(registerRequest.password());
       User user = userMapper.toEntity(registerRequest, encodedPassword);
       User savedUser = userRepository.save(user);
       return userMapper.toResponse(savedUser);
    }

    @Override
    public boolean isOwner(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == User.Role.OWNER || user.getRole() == User.Role.ADMIN)
                .orElse(false);
    }

    @Override
    public UserResponse getProfile(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElse(null);
    }

    @Override
    public Optional<Long> getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId);
    }
}
