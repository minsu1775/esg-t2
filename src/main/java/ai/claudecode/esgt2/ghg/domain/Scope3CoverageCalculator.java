package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Scope3CoverageCalculator {

    private static final BigDecimal THRESHOLD = new BigDecimal("95.00");

    private Scope3CoverageCalculator() {}

    /**
     * 배출량 기반 95% 커버리지 계산 (GHG Protocol Phase 1 Update 2026-03).
     *
     * @param includedEmissions          실제 배출량 보유 카테고리 (카테고리번호 → tCO2e)
     * @param estimatedExcludedEmissions 제외 카테고리 추정 배출량 (카테고리번호 → tCO2e)
     * @param exclusionReasons           제외 사유 (카테고리번호 → 사유 텍스트)
     */
    public static Scope3CoverageReport calculate(
            UUID tenantId, UUID entityId, int reportingYear,
            Map<Integer, BigDecimal> includedEmissions,
            Map<Integer, BigDecimal> estimatedExcludedEmissions,
            Map<Integer, String> exclusionReasons) {

        BigDecimal includedTotal = includedEmissions.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal excludedTotal = estimatedExcludedEmissions.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEstimated = includedTotal.add(excludedTotal);

        BigDecimal coveragePct;
        if (totalEstimated.compareTo(BigDecimal.ZERO) == 0) {
            coveragePct = new BigDecimal("100.00");
        } else {
            coveragePct = includedTotal
                .divide(totalEstimated, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        }

        List<Integer> sortedIncluded = includedEmissions.keySet().stream()
            .sorted().toList();
        List<Integer> sortedExcluded = estimatedExcludedEmissions.keySet().stream()
            .sorted().toList();

        return new Scope3CoverageReport(
            UUID.randomUUID(),
            tenantId, entityId, reportingYear,
            sortedIncluded,
            sortedExcluded,
            exclusionReasons,
            coveragePct,
            coveragePct.compareTo(THRESHOLD) >= 0
        );
    }
}
