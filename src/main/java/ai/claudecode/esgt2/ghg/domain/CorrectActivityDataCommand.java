package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 활동 데이터 정정 커맨드.
 * correctionReason은 필수 — 빈 값 시 IllegalArgumentException (01-domain-architecture.md 검증 우선 원칙).
 */
public record CorrectActivityDataCommand(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality,
    Integer lifetimeYears,
    String correctionReason
) {
    public CorrectActivityDataCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId 필수");
        if (entityId == null) throw new IllegalArgumentException("entityId 필수");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category 필수");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("활동량은 0 이상이어야 합니다");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit 필수");
        if (countryCode == null || countryCode.isBlank()) throw new IllegalArgumentException("countryCode 필수");
        if (correctionReason == null || correctionReason.isBlank())
            throw new IllegalArgumentException("정정 사유는 필수입니다");
    }
}
