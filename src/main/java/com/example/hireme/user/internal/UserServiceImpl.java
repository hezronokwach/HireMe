package com.example.hireme.user.internal;

import com.example.hireme.user.UserService;
import com.example.hireme.user.dto.RegisterRequest;
import com.example.hireme.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

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
        // Implementation will come later
        return false;
    }

    @Override
    public UserResponse getProfile(Long userId) {
        // Implementation will come later
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElse(null);
    }
}
