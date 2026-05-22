package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "공급업체 초대 응답")
public record SupplierInvitationResponse(
    @Schema(description = "초대 ID") UUID id,
    @Schema(description = "초대 이메일") String email,
    @Schema(description = "스코프 법인 ID") UUID entityId,
    @Schema(description = "초대 상태") String status,
    @Schema(description = "만료 일시") OffsetDateTime expiresAt
) {}
