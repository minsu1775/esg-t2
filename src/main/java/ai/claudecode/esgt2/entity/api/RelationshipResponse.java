package ai.claudecode.esgt2.entity.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "법인 관계 정보")
public record RelationshipResponse(
    @Schema(description = "관계 ID") UUID id,
    @Schema(description = "부모 법인 ID") UUID parentId,
    @Schema(description = "자식 법인 ID") UUID childId,
    @Schema(description = "지분율") BigDecimal ownershipRatio,
    @Schema(description = "연결 방법") ConsolidationMethod method,
    @Schema(description = "유효 시작일") LocalDate effectiveFrom,
    @Schema(description = "유효 종료일") LocalDate effectiveTo
) {}
