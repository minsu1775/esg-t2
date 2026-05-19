package ai.claudecode.esgt2.entity.internal;

import ai.claudecode.esgt2.entity.api.AuthService;
import ai.claudecode.esgt2.entity.api.LoginRequest;
import ai.claudecode.esgt2.entity.api.TokenResponse;
import ai.claudecode.esgt2.entity.infra.UserRepository;
import ai.claudecode.esgt2.entity.infra.UserRoleRepository;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultAuthService implements AuthService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var user = userRepository.findActiveByTenantIdAndEmail(request.tenantId(), request.email())
            .orElseThrow(() -> new EsgException(EsgErrorCode.ACCESS_DENIED, "이메일 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new EsgException(EsgErrorCode.ACCESS_DENIED, "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        List<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
            .map(r -> r.getRole())
            .toList();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getTenantId(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getTenantId());
        return new TokenResponse(accessToken, refreshToken);
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        if (isBlacklisted(refreshToken)) {
            throw new EsgException(EsgErrorCode.ACCESS_DENIED, "만료된 리프레시 토큰입니다.");
        }

        var jwt = jwtTokenProvider.decode(refreshToken);
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenantId"));

        var user = userRepository.findById(userId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.ACCESS_DENIED, "사용자를 찾을 수 없습니다."));

        List<String> roles = userRoleRepository.findByUserId(userId).stream()
            .map(r -> r.getRole())
            .toList();

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, tenantId, roles);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, tenantId);

        blacklist(refreshToken);
        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(String refreshToken) {
        blacklist(refreshToken);
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    private void blacklist(String token) {
        redisTemplate.opsForValue().set(
            BLACKLIST_PREFIX + token, "1",
            Duration.ofDays(REFRESH_TOKEN_EXPIRY_DAYS));
    }
}
