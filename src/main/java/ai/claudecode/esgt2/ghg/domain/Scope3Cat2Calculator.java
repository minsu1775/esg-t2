package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public final class Scope3Cat2Calculator {

    private Scope3Cat2Calculator() {}

    // 자본재 취득액 기반 배출량: acquisitionCost × factor (tCO2e/통화단위)
    public static BigDecimal computeEmission(BigDecimal acquisitionCost, BigDecimal factorValue) {
        if (acquisitionCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("자본재 취득액은 0 이상이어야 합니다: " + acquisitionCost);
        }
        return EmissionCalculator.computeEmission(acquisitionCost, factorValue);
    }
}
