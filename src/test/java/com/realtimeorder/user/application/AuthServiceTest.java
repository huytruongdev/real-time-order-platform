package com.realtimeorder.user.application;

import com.realtimeorder.user.application.AuthExceptions.InvalidCredentialsException;
import com.realtimeorder.user.application.AuthExceptions.InvalidRefreshTokenException;
import com.realtimeorder.user.domain.RefreshToken;
import com.realtimeorder.user.infrastructure.RefreshTokenRepository;
import com.realtimeorder.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho các nhánh khó tái hiện qua HTTP: token hết hạn (cần điều khiển thời gian),
 * và hành vi reuse detection.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    UserRepository userRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AccessTokenIssuer accessTokenIssuer;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                accessTokenIssuer,
                new RefreshTokenProperties(Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refreshWithExpiredTokenIsRejected() {
        String raw = "expired-token";
        RefreshToken expired = RefreshToken.issueNewFamily(
                UUID.randomUUID(), RefreshTokenCodec.hash(raw), NOW.minusSeconds(1), NOW.minus(Duration.ofDays(7)));
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenCodec.hash(raw)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void reusingRevokedTokenRevokesWholeFamily() {
        String raw = "already-rotated-token";
        RefreshToken rotated = RefreshToken.issueNewFamily(
                UUID.randomUUID(), RefreshTokenCodec.hash(raw), NOW.plus(Duration.ofDays(7)), NOW);
        rotated.rotate("next-hash", NOW.plus(Duration.ofDays(7)), NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenCodec.hash(raw)))
                .thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> authService.refresh(raw)).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeFamily(eq(rotated.getFamilyId()), eq(NOW));
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void loginWithUnknownEmailStillRunsPasswordCheck() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
        // Chạy BCrypt với hash giả để thời gian phản hồi tương đương trường hợp sai password.
        verify(passwordEncoder).matches("password123", "dummy-hash");
    }
}
