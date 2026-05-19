package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.ConsolidationMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "법인 관계 설정 요청")
public record SetRelationshipRequest(
    @Schema(description = "자식 법인 ID") @NotNull UUID childId,
    @Schema(description = "지분율 (0.0001 ~ 1.0)", example = "0.51")
    @NotNull @DecimalMin(value = "0.0001") @DecimalMax(value = "1.0") BigDecimal ownershipRatio,
    @Schema(description = "연결 방법 (EQUITY | OPERATIONAL_CONTROL)") @NotNull ConsolidationMethod method,
    @Schema(description = "유효 시작일", example = "2024-01-01") @NotNull LocalDate effectiveFrom,
    @Schema(description = "유효 종료일 (null = 무기한)", example = "2024-12-31") LocalDate effectiveTo
) {}
