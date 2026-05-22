package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "공급업체 활동 데이터 등록 요청")
public record SupplierDataRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull @Min(2020) int reportingYear,

    @Schema(description = "GHG 카테고리", example = "SCOPE3_CAT1")
    @NotBlank String category,

    @Schema(description = "세부 카테고리", example = "ELECTRONICS")
    String subCategory,

    @Schema(description = "활동량", example = "10000.0")
    @NotNull @Positive BigDecimal quantity,

    @Schema(description = "단위", example = "KRW")
    @NotBlank String unit,

    @Schema(description = "국가 코드", example = "KR")
    @NotBlank String countryCode
) {}
