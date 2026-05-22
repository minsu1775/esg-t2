package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "공급업체 초대 요청")
public record InviteSupplierRequest(
    @Schema(description = "초대할 이메일", example = "supplier@example.com")
    @NotBlank @Email String email,

    @Schema(description = "스코프 법인 ID")
    @NotNull UUID entityId
) {}
