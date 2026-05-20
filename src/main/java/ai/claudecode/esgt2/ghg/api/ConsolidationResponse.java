package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "연결 집계 결과")
public record ConsolidationResponse(
        @Schema(description = "집계 레코드 ID") UUID id,
        @Schema(description = "루트 법인 ID") UUID rootEntityId,
        @Schema(description = "보고 연도", example = "2025") int reportingYear,
        @Schema(description = "스코프 (현재 ALL — 향후 SCOPE1·2·3 분리 예정)", example = "ALL") String scope,
        @Schema(description = "온실가스 유형", example = "CO2E") String ghgType,
        @Schema(description = "연결 방법 (EQUITY | OPERATIONAL_CONTROL)", example = "EQUITY") String consolidationMethod,
        @Schema(description = "연결 배출량 합계 (tCO2e)", example = "524.392000") BigDecimal totalEmission,
        @Schema(description = "법인별 기여분 상세") List<ConsolidationItemResponse> contributions,
        @Schema(description = "산출 시각 (UTC)") Instant createdAt) {
}
