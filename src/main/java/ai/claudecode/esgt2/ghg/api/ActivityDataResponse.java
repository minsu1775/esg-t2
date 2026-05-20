package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "활동 데이터 응답")
public record ActivityDataResponse(
    @Schema(description = "활동 데이터 ID") UUID id,
    @Schema(description = "법인 ID") UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "GHG 카테고리") String category,
    @Schema(description = "세부 카테고리") String subCategory,
    @Schema(description = "활동량") BigDecimal quantity,
    @Schema(description = "단위") String unit,
    @Schema(description = "국가 코드") String countryCode,
    @Schema(description = "데이터 출처") String dataSource,
    @Schema(description = "데이터 품질") String dataQuality,
    @Schema(description = "처리 상태") String status,
    @Schema(description = "생성 시각") Instant createdAt
) {}
