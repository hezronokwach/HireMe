package com.example.hireme.user.internal;

import com.example.hireme.user.RegisterRequest;
import com.example.hireme.user.Role;
import com.example.hireme.user.UserContact;
import com.example.hireme.user.UserResponse;
import com.example.hireme.user.UserService;
import com.example.hireme.user.internal.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
           throw UserAlreadyExistsException.email();
       }
       // 0712345678 and +254712345678 are the same number, so a number counts as taken in either form.
       if (userRepository.existsByPhoneNumberIn(phoneForms(registerRequest.phoneNumber()))) {
           throw UserAlreadyExistsException.phoneNumber();
       }
       String encodedPassword = passwordEncoder.encode(registerRequest.password());
       User user = userMapper.toEntity(registerRequest, encodedPassword);
       User savedUser = userRepository.save(user);
       return userMapper.toResponse(savedUser);
    }

    /** A Kenyan mobile number in both forms the API accepts: 07XXXXXXXX and +2547XXXXXXXX. */
    static java.util.List<String> phoneForms(String phoneNumber) {
        String phone = phoneNumber == null ? "" : phoneNumber.strip();
        if (phone.startsWith("+254")) {
            return java.util.List.of(phone, "0" + phone.substring(4));
        }
        if (phone.startsWith("0")) {
            return java.util.List.of(phone, "+254" + phone.substring(1));
        }
        return java.util.List.of(phone);
    }

    @Override
    public boolean isOwner(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    @Override
    public boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ADMIN)
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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, UserContact> getContacts(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(Set.copyOf(userIds)).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> new UserContact(user.getId(), user.getFullName(), user.getPhoneNumber())));
    }
}
