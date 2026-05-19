package ai.claudecode.esgt2.entity.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEntityRelationshipCommand(
    UUID tenantId,
    UUID parentId,
    UUID childId,
    BigDecimal ownershipRatio,
    ConsolidationMethod method,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
