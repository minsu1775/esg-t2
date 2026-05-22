package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "활동 데이터 버전 이력 항목")
public record ActivityDataVersionResponse(
    @Schema(description = "레코드 ID") UUID id,
    @Schema(description = "정정 원본 ID (null = 최초 등록)") UUID correctionOf,
    @Schema(description = "정정 사유") String correctionReason,
    @Schema(description = "활동량") BigDecimal quantity,
    @Schema(description = "단위") String unit,
    @Schema(description = "GHG 카테고리") String category,
    @Schema(description = "세부 카테고리") String subCategory,
    @Schema(description = "상태") String status,
    @Schema(description = "생성 시각") Instant createdAt
) {}
