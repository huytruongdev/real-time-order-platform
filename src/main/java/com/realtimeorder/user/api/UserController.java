package com.realtimeorder.user.api;

import com.realtimeorder.user.api.AuthDtos.UserResponse;
import com.realtimeorder.user.application.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final AuthService authService;

    UserController(AuthService authService) {
        this.authService = authService;
    }

    /** User hiện tại, xác định bằng claim {@code sub} của access token. */
    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.getProfile(UUID.fromString(jwt.getSubject())));
    }
}
