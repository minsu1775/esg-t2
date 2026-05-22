package ai.claudecode.esgt2.rpt.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 보고서 생성 커맨드.
 * emissionsByScope: scope 키("SCOPE1","SCOPE2_LB","SCOPE2_MB","SCOPE3") → 배출량(tCO2e).
 */
public record CreateReportCommand(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String framework,           // "KSSB2"
    Map<String, BigDecimal> emissionsByScope
) {
    public CreateReportCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId 필수");
        if (entityId == null) throw new IllegalArgumentException("entityId 필수");
        if (framework == null || framework.isBlank()) throw new IllegalArgumentException("framework 필수");
        if (emissionsByScope == null) emissionsByScope = Map.of();
    }
}
