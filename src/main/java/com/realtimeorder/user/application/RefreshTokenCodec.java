package com.realtimeorder.user.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Sinh và hash refresh token.
 *
 * Refresh token là chuỗi ngẫu nhiên 256 bit (opaque), không phải JWT: server luôn phải tra DB
 * để kiểm tra revoke, nên không cần tự chứa thông tin.
 *
 * Hash bằng SHA-256 thay vì BCrypt: BCrypt chậm có chủ đích để chống brute-force password
 * có entropy thấp. Token 256 bit ngẫu nhiên không thể brute-force, nên SHA-256 là đủ,
 * và hash xác định (deterministic) cho phép tra cứu trực tiếp bằng unique index.
 */
final class RefreshTokenCodec {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenCodec() {
    }

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
