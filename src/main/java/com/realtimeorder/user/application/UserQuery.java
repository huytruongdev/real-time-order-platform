package com.realtimeorder.user.application;

import com.realtimeorder.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Public interface để module khác tra cứu user (ví dụ Catalog kiểm tra owner của restaurant).
 *
 * Module khác không được dùng {@code UserRepository} hoặc entity {@code User} trực tiếp.
 */
@Service
public class UserQuery {

    private final UserRepository userRepository;

    public UserQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findById(UUID userId) {
        return userRepository.findById(userId).map(UserProfile::from);
    }
}
