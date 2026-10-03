package com.realtimeorder.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param ttl thời gian sống của mỗi refresh token (mỗi lần rotation sinh token mới với ttl mới)
 */
@ConfigurationProperties(prefix = "security.refresh-token")
public record RefreshTokenProperties(Duration ttl) {

    public RefreshTokenProperties {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("security.refresh-token.ttl phải lớn hơn 0");
        }
    }
}
