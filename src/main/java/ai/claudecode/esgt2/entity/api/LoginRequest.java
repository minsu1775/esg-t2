package ai.claudecode.esgt2.entity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "로그인 요청")
public record LoginRequest(
    @Schema(description = "테넌트 ID") @NotNull UUID tenantId,
    @Schema(description = "이메일", example = "user@example.com") @Email @NotBlank String email,
    @Schema(description = "비밀번호") @NotBlank String password
) {}
