package ai.claudecode.esgt2.rpt.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "공시 보고서 응답")
public record ReportResponse(
    @Schema(description = "보고서 ID") UUID id,
    @Schema(description = "법인 ID") UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "프레임워크") String framework,
    @Schema(description = "상태 (DRAFT/SUBMITTED/APPROVED/REJECTED)") String status,
    @Schema(description = "스코프별 배출량 (tCO2e)") Map<String, BigDecimal> emissionsByScope,
    @Schema(description = "총 배출량 (tCO2e)") BigDecimal totalEmission,
    @Schema(description = "섹션 목록") List<SectionDto> sections,
    @Schema(description = "생성 시각") Instant generatedAt,
    @Schema(description = "승인 시각") Instant approvedAt
) {
    @Schema(description = "보고서 섹션")
    public record SectionDto(
        String itemCode, String title,
        BigDecimal value, BigDecimal yoyDelta
    ) {}
}
