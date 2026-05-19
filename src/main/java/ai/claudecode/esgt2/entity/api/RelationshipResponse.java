package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.ConsolidationMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RelationshipResponse(
    UUID id,
    UUID parentId,
    UUID childId,
    BigDecimal ownershipRatio,
    ConsolidationMethod method,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
