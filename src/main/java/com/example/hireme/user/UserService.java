package com.example.hireme.user;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface UserService {
    UserResponse register(RegisterRequest registerRequest);
    boolean isOwner(Long userId);
    boolean isAdmin(Long userId);
    UserResponse getProfile(Long userId);
    Optional<Long> getUserIdByEmail(String email);

    /** Contact details of several users in one query, by id. Ids with no user are left out. */
    Map<Long, UserContact> getContacts(Collection<Long> userIds);
}
