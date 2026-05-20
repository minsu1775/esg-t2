package ai.claudecode.esgt2.ghg.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ConsolidationItemResponse(
        UUID entityId,
        BigDecimal ownershipRatio,
        BigDecimal weightedEmission) {
}
