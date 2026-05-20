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

    @Schema(description = "GHG 카테고리", example = "SCOPE3_CAT1")
    @NotBlank String category,

    @Schema(description = "세부 카테고리 (선택)", example = "ELECTRONICS")
    String subCategory,

    @Schema(description = "활동량 (Cat.1/2: 지출금액, Cat.11: 판매량)", example = "10000.0")
    @NotNull @PositiveOrZero BigDecimal quantity,

    @Schema(description = "단위 (Cat.1/2: 통화코드, Cat.11: units)", example = "KRW")
    @NotBlank String unit,

    @Schema(description = "국가 코드 (ISO 3166-1)", example = "KR")
    @NotBlank String countryCode,

    @Schema(description = "데이터 출처", example = "MANUAL")
    String dataSource,

    @Schema(description = "데이터 품질 (Cat.1은 자동 결정)", example = "AVERAGE_DATA")
    String dataQuality,

    @Schema(description = "제품 사용기간 (Cat.11 전용, 단위: 년)", example = "8")
    Integer lifetimeYears
) {}
