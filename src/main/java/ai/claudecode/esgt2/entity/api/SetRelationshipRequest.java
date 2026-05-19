package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.ConsolidationMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SetRelationshipRequest(
    @NotNull UUID childId,
    @NotNull @DecimalMin(value = "0.0001") @DecimalMax(value = "1.0") BigDecimal ownershipRatio,
    @NotNull ConsolidationMethod method,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo
) {}
