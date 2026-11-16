package com.example.hireme.user.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    boolean existsByPhoneNumberIn(java.util.Collection<String> phoneNumbers);
    Optional<User> findByEmail(String email);
}