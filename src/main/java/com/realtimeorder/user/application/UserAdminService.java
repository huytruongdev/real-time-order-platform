package com.realtimeorder.user.application;

import com.realtimeorder.user.application.AuthExceptions.EmailAlreadyUsedException;
import com.realtimeorder.user.application.AuthExceptions.RoleNotAssignableException;
import com.realtimeorder.user.domain.Role;
import com.realtimeorder.user.domain.User;
import com.realtimeorder.user.infrastructure.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

/**
 * Tạo user có role khác CUSTOMER (ADR-035).
 *
 * - ADMIN chỉ được tạo bằng bootstrap lúc khởi động ({@link #ensureAdmin}), không qua API.
 * - RESTAURANT/DRIVER do Admin tạo qua {@code POST /api/v1/admin/users}.
 */
@Service
public class UserAdminService {

    private static final Set<Role> ASSIGNABLE_ROLES = Set.of(Role.RESTAURANT, Role.DRIVER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserProfile createUser(String email, String rawPassword, String name, Role role) {
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new RoleNotAssignableException();
        }
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        User user = User.createWithRole(
                normalizedEmail, passwordEncoder.encode(rawPassword), name, role, clock.instant());
        try {
            // Giống register: unique constraint là chốt chặn cuối khi hai request trùng email chạy đồng thời.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        return UserProfile.from(user);
    }

    /**
     * Tạo ADMIN nếu email chưa tồn tại. Idempotent: gọi lại ở mỗi lần khởi động không tạo thêm user.
     *
     * Nếu email đã tồn tại (kể cả với role khác), không thay đổi gì: bootstrap không được âm thầm
     * nâng quyền một tài khoản có sẵn.
     *
     * @return true nếu admin vừa được tạo
     */
    @Transactional
    public boolean ensureAdmin(String email, String rawPassword, String name) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return false;
        }
        try {
            // Nhiều instance khởi động cùng lúc: chỉ một instance insert thành công.
            userRepository.saveAndFlush(User.createWithRole(
                    normalizedEmail, passwordEncoder.encode(rawPassword), name, Role.ADMIN, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
