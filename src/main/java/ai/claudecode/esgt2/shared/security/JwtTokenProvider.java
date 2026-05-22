package ai.claudecode.esgt2.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final NimbusJwtDecoder jwtDecoder;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(this.secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** 기존 호출부 호환성 유지 (entityId 없음). */
    public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles) {
        return generateAccessToken(userId, tenantId, null, roles);
    }

    /** SUPPLIER 사용자용 — entityId를 JWT 클레임에 포함. */
    public String generateAccessToken(UUID userId, UUID tenantId, UUID entityId, List<String> roles) {
        return encode(userId, tenantId, entityId, roles, jwtProperties.accessTokenExpirySeconds());
    }

    public String generateRefreshToken(UUID userId, UUID tenantId) {
        return encode(userId, tenantId, null, List.of(), jwtProperties.refreshTokenExpirySeconds());
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    NimbusJwtDecoder decoder() {
        return jwtDecoder;
    }

    private String encode(UUID userId, UUID tenantId, UUID entityId, List<String> roles, long expirySeconds) {
        var now = Instant.now();
        var builder = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("tenantId", tenantId.toString())
            .claim("roles", roles)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(expirySeconds));
        if (entityId != null) {
            builder.claim("entityId", entityId.toString());
        }
        var claims = builder.build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
