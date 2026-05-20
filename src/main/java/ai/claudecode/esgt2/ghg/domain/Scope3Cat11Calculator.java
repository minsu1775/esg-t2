package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Scope3Cat11Calculator {

    private Scope3Cat11Calculator() {}

    // 연간 귀속 배출량 = (판매량 × 생애주기 배출계수) ÷ 사용기간
    // GHG Protocol: 판매연도에 전체 생애 배출을 한 번에 계상하지 않고 사용기간으로 분할
    public static BigDecimal computeAnnualEmission(
            BigDecimal quantity, BigDecimal factorValue, Integer lifetimeYears) {
        if (lifetimeYears == null || lifetimeYears <= 0) {
            throw new IllegalArgumentException("사용기간(lifetimeYears)은 1 이상이어야 합니다: " + lifetimeYears);
        }
        BigDecimal lifetimeEmission = EmissionCalculator.computeEmission(quantity, factorValue);
        return lifetimeEmission.divide(BigDecimal.valueOf(lifetimeYears), 6, RoundingMode.HALF_UP);
    }
}
