package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Webhook 활동 데이터 단건")
public record WebhookActivityDataItem(
    @Schema(description = "법인 ID") @NotNull UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "GHG 카테고리 (예: SCOPE3_CAT1)") @NotBlank String category,
    @Schema(description = "하위 카테고리") String subCategory,
    @Schema(description = "활동량") @NotNull @Positive BigDecimal quantity,
    @Schema(description = "단위 (예: KRW, unit)") @NotBlank String unit,
    @Schema(description = "국가 코드 (ISO 2자리)") @NotBlank String countryCode,
    @Schema(description = "데이터 출처 (기본값 WEBHOOK)") String dataSource,
    @Schema(description = "데이터 품질") String dataQuality,
    @Schema(description = "사용기간 연수 (Cat.11 전용)") Integer lifetimeYears
) {}
