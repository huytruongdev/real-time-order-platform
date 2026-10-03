package com.realtimeorder.user.application;

import com.realtimeorder.user.application.AuthExceptions.EmailAlreadyUsedException;
import com.realtimeorder.user.application.AuthExceptions.InvalidCredentialsException;
import com.realtimeorder.user.application.AuthExceptions.InvalidRefreshTokenException;
import com.realtimeorder.user.application.AuthExceptions.UserNotFoundException;
import com.realtimeorder.user.domain.RefreshToken;
import com.realtimeorder.user.domain.User;
import com.realtimeorder.user.infrastructure.RefreshTokenRepository;
import com.realtimeorder.user.infrastructure.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenProperties refreshTokenProperties;
    private final Clock clock;

    /**
     * Hash giả dùng khi email không tồn tại, để login sai email và login sai password
     * tốn thời gian xấp xỉ nhau (giảm khả năng dò email qua thời gian phản hồi).
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AccessTokenIssuer accessTokenIssuer,
                       RefreshTokenProperties refreshTokenProperties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenProperties = refreshTokenProperties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-protection");
    }

    @Transactional
    public UserProfile register(String email, String rawPassword, String name) {
        String normalizedEmail = User.normalizeEmail(email);

        // Fast path: báo lỗi rõ ràng cho trường hợp phổ biến.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException();
        }

        User user = User.registerCustomer(normalizedEmail, passwordEncoder.encode(rawPassword), name, clock.instant());
        try {
            // Hai request đăng ký cùng email đồng thời có thể cùng vượt qua existsByEmail.
            // Unique constraint trong DB là chốt chặn cuối cùng; flush ngay để bắt lỗi tại đây.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException();
        }
        return UserProfile.from(user);
    }

    @Transactional
    public AuthTokens login(String email, String rawPassword) {
        User user = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Instant now = clock.instant();
        String rawRefreshToken = RefreshTokenCodec.generate();
        RefreshToken refreshToken = RefreshToken.issueNewFamily(
                user.getId(), RefreshTokenCodec.hash(rawRefreshToken), now.plus(refreshTokenProperties.ttl()), now);
        refreshTokenRepository.save(refreshToken);

        return buildTokens(user, rawRefreshToken, refreshToken, now);
    }

    /**
     * Refresh token rotation với reuse detection.
     *
     * {@code noRollbackFor}: khi phát hiện reuse, ta revoke cả family rồi throw exception.
     * Mặc định Spring rollback khi có RuntimeException, nghĩa là việc revoke family cũng bị rollback.
     * Vì vậy transaction phải commit dù method kết thúc bằng exception.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(RefreshTokenCodec.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            // Token đã bị rotate/revoke mà vẫn được dùng: có thể token đã bị đánh cắp.
            // Không phân biệt được ai là chủ thật, nên vô hiệu hóa toàn bộ family.
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);

        String newRawRefreshToken = RefreshTokenCodec.generate();
        RefreshToken next = current.rotate(
                RefreshTokenCodec.hash(newRawRefreshToken), now.plus(refreshTokenProperties.ttl()), now);
        refreshTokenRepository.save(next);

        return buildTokens(user, newRawRefreshToken, next, now);
    }

    /**
     * Revoke refresh token được gửi lên. Idempotent: token không tồn tại hoặc đã revoke thì không làm gì.
     * Access token đang còn hạn vẫn dùng được đến khi hết hạn (đặc điểm của stateless JWT).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenCodec.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(UserProfile::from)
                .orElseThrow(UserNotFoundException::new);
    }

    private AuthTokens buildTokens(User user, String rawRefreshToken, RefreshToken refreshToken, Instant now) {
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user, now);
        return new AuthTokens(
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefreshToken,
                refreshToken.getExpiresAt());
    }
}
