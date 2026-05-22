package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "검증 스냅샷 응답")
public record SnapshotResponse(
    @Schema(description = "스냅샷 ID") UUID id,
    @Schema(description = "테넌트 ID") UUID tenantId,
    @Schema(description = "보고서 ID") UUID reportId,
    @Schema(description = "SHA-256 해시 (64자)") String snapshotHash,
    @Schema(description = "생성 시각") Instant createdAt,
    @Schema(description = "서명 완료 여부") boolean signed
) {}
