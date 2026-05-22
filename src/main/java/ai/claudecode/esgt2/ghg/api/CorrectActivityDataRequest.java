package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "활동 데이터 정정 요청")
public record CorrectActivityDataRequest(
    @Schema(description = "보고 연도") @NotNull @Min(2020) int reportingYear,
    @Schema(description = "GHG 카테고리") @NotBlank String category,
    @Schema(description = "세부 카테고리 (선택)") String subCategory,
    @Schema(description = "정정 활동량") @NotNull @PositiveOrZero BigDecimal quantity,
    @Schema(description = "단위") @NotBlank String unit,
    @Schema(description = "국가 코드 (ISO 3166-1)") @NotBlank String countryCode,
    @Schema(description = "데이터 출처") String dataSource,
    @Schema(description = "데이터 품질") String dataQuality,
    @Schema(description = "제품 사용기간 (Cat.11 전용, 단위: 년)") Integer lifetimeYears,
    @Schema(description = "정정 사유 (필수)") @NotBlank String correctionReason
) {}
