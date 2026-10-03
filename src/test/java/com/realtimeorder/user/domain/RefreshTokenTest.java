package com.realtimeorder.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void newTokenIsActive() {
        RefreshToken token = RefreshToken.issueNewFamily(USER_ID, "hash-1", NOW.plus(Duration.ofDays(7)), NOW);

        assertThat(token.isActive(NOW)).isTrue();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getFamilyId()).isNotNull();
    }

    @Test
    void tokenIsExpiredExactlyAtExpiresAt() {
        Instant expiresAt = NOW.plus(Duration.ofDays(7));
        RefreshToken token = RefreshToken.issueNewFamily(USER_ID, "hash-1", expiresAt, NOW);

        assertThat(token.isExpired(expiresAt.minusMillis(1))).isFalse();
        assertThat(token.isExpired(expiresAt)).isTrue();
        assertThat(token.isActive(expiresAt)).isFalse();
    }

    @Test
    void rotateRevokesCurrentAndCreatesNextTokenInSameFamily() {
        RefreshToken current = RefreshToken.issueNewFamily(USER_ID, "hash-1", NOW.plus(Duration.ofDays(7)), NOW);
        Instant rotatedAt = NOW.plus(Duration.ofHours(1));

        RefreshToken next = current.rotate("hash-2", rotatedAt.plus(Duration.ofDays(7)), rotatedAt);

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedAt()).isEqualTo(rotatedAt);
        assertThat(current.getReplacedBy()).isEqualTo(next.getId());

        assertThat(next.isActive(rotatedAt)).isTrue();
        assertThat(next.getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(next.getUserId()).isEqualTo(USER_ID);
        assertThat(next.getId()).isNotEqualTo(current.getId());
    }

    @Test
    void revokeIsIdempotentAndKeepsFirstRevokedAt() {
        RefreshToken token = RefreshToken.issueNewFamily(USER_ID, "hash-1", NOW.plus(Duration.ofDays(7)), NOW);

        token.revoke(NOW.plusSeconds(10));
        token.revoke(NOW.plusSeconds(20));

        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
