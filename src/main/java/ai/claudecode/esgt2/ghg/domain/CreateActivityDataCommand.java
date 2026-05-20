package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateActivityDataCommand(
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
    Integer lifetimeYears   // Cat.11 전용 (nullable)
) {
    public CreateActivityDataCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId 필수");
        if (entityId == null) throw new IllegalArgumentException("entityId 필수");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category 필수");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("활동량은 0 이상이어야 합니다");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit 필수");
        if (countryCode == null || countryCode.isBlank()) throw new IllegalArgumentException("countryCode 필수");
    }
}
