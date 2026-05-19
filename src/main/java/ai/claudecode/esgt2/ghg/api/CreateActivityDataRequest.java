package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "활동 데이터 등록 요청")
public record CreateActivityDataRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull @Min(2020) int reportingYear,

    @Schema(description = "GHG 카테고리 (SCOPE1_FUEL, SCOPE2_ELECTRICITY 등)", example = "SCOPE1_FUEL")
    @NotBlank String category,

    @Schema(description = "세부 카테고리 (선택)", example = "DIESEL_AUTO")
    String subCategory,

    @Schema(description = "활동량", example = "100.5")
    @NotNull @PositiveOrZero BigDecimal quantity,

    @Schema(description = "단위", example = "kL")
    @NotBlank String unit,

    @Schema(description = "국가 코드 (ISO 3166-1)", example = "KR")
    @NotBlank String countryCode,

    @Schema(description = "데이터 출처", example = "MANUAL")
    String dataSource,

    @Schema(description = "데이터 품질", example = "AVERAGE_DATA")
    String dataQuality
) {}
