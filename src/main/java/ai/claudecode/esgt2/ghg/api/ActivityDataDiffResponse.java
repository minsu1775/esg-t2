package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "활동 데이터 정정 전·후 비교")
public record ActivityDataDiffResponse(
    @Schema(description = "원본 ID") UUID originalId,
    @Schema(description = "정정 후 ID") UUID correctedId,
    @Schema(description = "정정 사유") String correctionReason,
    @Schema(description = "원본 활동량") BigDecimal originalQuantity,
    @Schema(description = "정정 활동량") BigDecimal correctedQuantity,
    @Schema(description = "원본 단위") String originalUnit,
    @Schema(description = "정정 단위") String correctedUnit,
    @Schema(description = "원본 카테고리") String originalCategory,
    @Schema(description = "정정 카테고리") String correctedCategory,
    @Schema(description = "원본 생성 시각") Instant originalCreatedAt,
    @Schema(description = "정정 생성 시각") Instant correctedCreatedAt
) {}
