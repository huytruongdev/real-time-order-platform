package com.realtimeorder.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;

/**
 * Cấu hình JWT access token.
 *
 * @param issuer         giá trị claim {@code iss}, được kiểm tra khi decode
 * @param secret         HMAC-SHA256 secret dạng Base64, tối thiểu 256 bit
 * @param accessTokenTtl thời gian sống của access token
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer phải được cấu hình");
        }
        if (secret == null || Base64.getDecoder().decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("security.jwt.secret phải là Base64 của tối thiểu 256 bit");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("security.jwt.access-token-ttl phải lớn hơn 0");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
}
