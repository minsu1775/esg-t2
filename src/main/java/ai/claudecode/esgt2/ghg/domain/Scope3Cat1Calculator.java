package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public final class Scope3Cat1Calculator {

    private Scope3Cat1Calculator() {}

    // 지출 기반 배출량: spend × factor (tCO2e/통화단위)
    public static BigDecimal computeEmission(BigDecimal spendAmount, BigDecimal factorValue) {
        if (spendAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("지출액은 0 이상이어야 합니다: " + spendAmount);
        }
        return EmissionCalculator.computeEmission(spendAmount, factorValue);
    }

    // dataSource 기반 데이터 품질 자동 결정
    public static String deriveDataQuality(String dataSource) {
        if (dataSource == null) return "SPEND_BASED";
        return switch (dataSource) {
            case "SUPPLIER_PORTAL" -> "SUPPLIER_SPECIFIC";
            case "API"             -> "AVERAGE_DATA";
            default                -> "SPEND_BASED";
        };
    }
}
