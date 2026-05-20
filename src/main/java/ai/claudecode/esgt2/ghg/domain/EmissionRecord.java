package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record EmissionRecord(
    UUID id,
    UUID tenantId,
    UUID entityId,
    UUID activityDataId,
    int reportingYear,
    String scope,
    Integer scope3Category,   // Scope 3 카테고리 번호 (1~16), Scope 1/2는 null
    String ghgType,
    UUID emissionFactorId,
    BigDecimal rawEmission
) {
    public static EmissionRecord calculate(
            UUID tenantId, UUID entityId, UUID activityDataId,
            int reportingYear, String scope, Integer scope3Category,
            String ghgType, UUID emissionFactorId, BigDecimal rawEmission) {
        if (rawEmission == null || rawEmission.signum() < 0) {
            throw new IllegalArgumentException("rawEmission must be non-negative");
        }
        return new EmissionRecord(
            UUID.randomUUID(), tenantId, entityId, activityDataId,
            reportingYear, scope, scope3Category, ghgType, emissionFactorId, rawEmission
        );
    }
}
