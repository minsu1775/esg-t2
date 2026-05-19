package ai.claudecode.esgt2.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles) {
        return encode(userId, tenantId, roles, jwtProperties.accessTokenExpirySeconds());
    }

    public String generateRefreshToken(UUID userId, UUID tenantId) {
        return encode(userId, tenantId, List.of(), jwtProperties.refreshTokenExpirySeconds());
    }

    public Jwt decode(String token) {
        return decoder().decode(token);
    }

    NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private String encode(UUID userId, UUID tenantId, List<String> roles, long expirySeconds) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("tenantId", tenantId.toString())
            .claim("roles", roles)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(expirySeconds))
            .build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private SecretKey secretKey() {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
