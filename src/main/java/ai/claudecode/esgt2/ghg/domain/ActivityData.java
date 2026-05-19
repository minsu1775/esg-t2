package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivityData(
    UUID id,
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality
) {
    public static ActivityData create(CreateActivityDataCommand cmd) {
        return new ActivityData(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : "MANUAL",
            cmd.dataQuality() != null ? cmd.dataQuality() : "AVERAGE_DATA"
        );
    }
}
