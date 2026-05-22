package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "공급업체 계정 활성화 요청")
public record ActivateSupplierRequest(
    @Schema(description = "초대 토큰 (이메일 링크에서 추출)")
    @NotNull UUID token,

    @Schema(description = "설정할 비밀번호 (최소 8자)")
    @NotBlank @Size(min = 8) String password
) {}
