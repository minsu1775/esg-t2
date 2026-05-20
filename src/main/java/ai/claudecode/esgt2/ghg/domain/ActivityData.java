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
    String dataQuality,
    BigDecimal standardValue,   // 기준 단위 변환값 (nullable)
    String standardUnit,        // 기준 단위 (nullable)
    Integer lifetimeYears       // Cat.11 전용 (nullable)
) {
    public static ActivityData create(CreateActivityDataCommand cmd) {
        String stdUnit = UnitConverter.standardUnitFor(cmd.unit());
        BigDecimal stdValue = null;
        if (stdUnit != null && !stdUnit.equals(cmd.unit())) {
            try {
                stdValue = UnitConverter.convert(cmd.quantity(), cmd.unit(), stdUnit);
            } catch (IllegalArgumentException ignored) {
                // 변환 불가 단위는 null 저장 (저장 차단 안 함)
            }
        } else if (stdUnit != null) {
            stdValue = cmd.quantity();
        }
        return new ActivityData(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : "MANUAL",
            cmd.dataQuality() != null ? cmd.dataQuality() : "AVERAGE_DATA",
            stdValue, stdUnit,
            cmd.lifetimeYears()
        );
    }
}
