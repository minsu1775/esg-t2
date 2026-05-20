package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public record CsvRow(
    int lineNumber,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality,
    Integer lifetimeYears
) {}
