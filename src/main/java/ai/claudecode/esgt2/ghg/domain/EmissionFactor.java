package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EmissionFactor(
    UUID id,
    String source,
    String category,
    String subCategory,
    String countryCode,
    int reportingYear,
    String gwpSource,
    BigDecimal factorValue,
    String unit,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean isActive
) {}
