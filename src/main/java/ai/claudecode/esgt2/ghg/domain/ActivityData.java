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
    Integer lifetimeYears,      // Cat.11 전용 (nullable)
    UUID correctionOf,          // 정정 원본 ID (null = 최초 등록)
    String correctionReason     // 정정 사유 (null = 최초 등록)
) {
    /** 최초 등록 팩토리 */
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
        String resolvedQuality = "SCOPE3_CAT1".equals(cmd.category())
            ? Scope3Cat1Calculator.deriveDataQuality(cmd.dataSource())
            : (cmd.dataQuality() != null ? cmd.dataQuality() : "AVERAGE_DATA");

        return new ActivityData(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : "MANUAL",
            resolvedQuality,
            stdValue, stdUnit,
            cmd.lifetimeYears(),
            null, null  // correctionOf, correctionReason
        );
    }

    /**
     * 정정 팩토리 — 원본을 참조하는 새 레코드 생성 (P1: INSERT-only).
     * correctionReason 검증은 CorrectActivityDataCommand compact constructor에서 수행.
     */
    public static ActivityData correct(ActivityData original, CorrectActivityDataCommand cmd) {
        String stdUnit = UnitConverter.standardUnitFor(cmd.unit());
        BigDecimal stdValue = null;
        if (stdUnit != null && !stdUnit.equals(cmd.unit())) {
            try {
                stdValue = UnitConverter.convert(cmd.quantity(), cmd.unit(), stdUnit);
            } catch (IllegalArgumentException ignored) {}
        } else if (stdUnit != null) {
            stdValue = cmd.quantity();
        }
        String resolvedQuality = "SCOPE3_CAT1".equals(cmd.category())
            ? Scope3Cat1Calculator.deriveDataQuality(cmd.dataSource())
            : (cmd.dataQuality() != null ? cmd.dataQuality() : original.dataQuality());

        return new ActivityData(
            UUID.randomUUID(),
            original.tenantId(), original.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : original.dataSource(),
            resolvedQuality,
            stdValue, stdUnit,
            cmd.lifetimeYears(),
            original.id(),          // correctionOf = 원본 ID
            cmd.correctionReason()
        );
    }
}
