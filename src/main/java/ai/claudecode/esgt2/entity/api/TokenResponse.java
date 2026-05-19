package ai.claudecode.esgt2.entity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT 토큰 응답")
public record TokenResponse(
    @Schema(description = "Access Token (15분 유효)") String accessToken,
    @Schema(description = "Refresh Token (7일 유효)") String refreshToken
) {}
