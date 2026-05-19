package ai.claudecode.esgt2.entity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record RefreshRequest(
    @Schema(description = "갱신할 Refresh Token") @NotBlank String refreshToken
) {}
