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
    String ghgType,
    UUID emissionFactorId,
    BigDecimal rawEmission
) {
    public static EmissionRecord calculate(
            UUID tenantId, UUID entityId, UUID activityDataId,
            int reportingYear, String scope, String ghgType,
            UUID emissionFactorId, BigDecimal rawEmission) {
        if (rawEmission == null || rawEmission.signum() < 0) {
            throw new IllegalArgumentException("rawEmission must be non-negative");
        }
        return new EmissionRecord(
            UUID.randomUUID(), tenantId, entityId, activityDataId,
            reportingYear, scope, ghgType, emissionFactorId, rawEmission
        );
    }
}
