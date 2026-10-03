package com.realtimeorder.user.api;

import com.realtimeorder.user.api.AuthDtos.CreateUserRequest;
import com.realtimeorder.user.api.AuthDtos.UserResponse;
import com.realtimeorder.user.application.UserAdminService;
import com.realtimeorder.user.domain.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Chỉ ADMIN (enforce trong {@code SecurityConfig} cho {@code /api/v1/admin/**}). */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final UserAdminService userAdminService;

    AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userAdminService.createUser(
                request.email(), request.password(), request.name(), Role.valueOf(request.role())));
    }
}
