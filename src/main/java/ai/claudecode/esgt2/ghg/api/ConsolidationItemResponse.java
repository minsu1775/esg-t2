package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "법인별 기여분 상세")
public record ConsolidationItemResponse(
        @Schema(description = "법인 ID") UUID entityId,
        @Schema(description = "실질 소유율 (Equity: 경로 곱, Operational Control: 1.0 또는 미포함)", example = "0.6000") BigDecimal ownershipRatio,
        @Schema(description = "가중 배출량 (tCO2e)", example = "155.760000") BigDecimal weightedEmission) {
}
