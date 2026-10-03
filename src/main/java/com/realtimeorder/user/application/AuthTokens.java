package com.realtimeorder.user.application;

import java.time.Instant;

public record AuthTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {
}
