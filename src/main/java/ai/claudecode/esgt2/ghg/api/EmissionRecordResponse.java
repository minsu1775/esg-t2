package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "배출량 기록 응답")
public record EmissionRecordResponse(
    @Schema(description = "배출량 기록 ID") UUID id,
    @Schema(description = "테넌트 ID") UUID tenantId,
    @Schema(description = "법인 ID") UUID entityId,
    @Schema(description = "활동 데이터 ID") UUID activityDataId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "GHG 스코프") String scope,
    @Schema(description = "GHG 유형") String ghgType,
    @Schema(description = "적용 배출계수 ID") UUID emissionFactorId,
    @Schema(description = "원시 배출량 (tCO2e)", example = "2.596000") BigDecimal rawEmission,
    @Schema(description = "연결 집계 포함 여부") boolean consolidated,
    @Schema(description = "산출 시각") Instant calculatedAt
) {}
