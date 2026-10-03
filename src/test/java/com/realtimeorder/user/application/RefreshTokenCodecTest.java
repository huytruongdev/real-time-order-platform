package com.realtimeorder.user.application;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCodecTest {

    @Test
    void generatesUrlSafe256BitTokens() {
        String token = RefreshTokenCodec.generate();

        // 32 byte Base64 URL-safe không padding = 43 ký tự
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatedTokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tokens.add(RefreshTokenCodec.generate());
        }
        assertThat(tokens).hasSize(1_000);
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        String hash = RefreshTokenCodec.hash("some-token");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(RefreshTokenCodec.hash("some-token")).isEqualTo(hash);
        assertThat(RefreshTokenCodec.hash("other-token")).isNotEqualTo(hash);
    }
}
