package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Scope 3 커버리지 보고서 생성 요청")
public record Scope3CoverageRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull int reportingYear,

    @Schema(description = "제외 카테고리 추정 배출량 (카테고리번호 → tCO2e)",
            example = "{\"4\": 1000.0, \"6\": 500.0}")
    Map<Integer, BigDecimal> estimatedExcludedEmissions,

    @Schema(description = "제외 카테고리 사유 (카테고리번호 → 사유)",
            example = "{\"4\": \"중요성 낮음\", \"6\": \"해당 없음\"}")
    Map<Integer, String> exclusionReasons
) {
    public Scope3CoverageRequest {
        if (estimatedExcludedEmissions == null) estimatedExcludedEmissions = Map.of();
        if (exclusionReasons == null) exclusionReasons = Map.of();
    }
}
