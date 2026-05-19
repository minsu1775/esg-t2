package ai.claudecode.esgt2.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    long accessTokenExpirySeconds,
    long refreshTokenExpirySeconds
) {}
