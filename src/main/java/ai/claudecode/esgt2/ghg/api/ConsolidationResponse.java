package ai.claudecode.esgt2.ghg.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsolidationResponse(
        UUID id,
        UUID rootEntityId,
        int reportingYear,
        String scope,
        String ghgType,
        String consolidationMethod,
        BigDecimal totalEmission,
        List<ConsolidationItemResponse> contributions,
        Instant createdAt) {
}
