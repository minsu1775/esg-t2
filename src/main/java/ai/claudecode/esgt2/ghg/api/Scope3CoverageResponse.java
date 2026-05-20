package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Scope 3 커버리지 보고서 응답")
public record Scope3CoverageResponse(
    @Schema(description = "보고서 ID") UUID id,
    @Schema(description = "법인 ID") UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "포함된 카테고리 번호 목록", example = "[1, 2, 11]")
    List<Integer> includedCategories,
    @Schema(description = "제외된 카테고리 번호 목록", example = "[4, 6]")
    List<Integer> excludedCategories,
    @Schema(description = "제외 카테고리 사유") Map<Integer, String> exclusionReasons,
    @Schema(description = "배출량 기반 커버리지 비율 (%)", example = "95.00")
    BigDecimal coveragePct,
    @Schema(description = "95% 임계값 충족 여부") boolean meets95PctThreshold,
    @Schema(description = "생성 시각") Instant generatedAt
) {}
