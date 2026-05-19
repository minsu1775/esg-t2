package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EmissionCalculator {

    private EmissionCalculator() {}

    // 배출량 = 활동량 × 배출계수 (scale=6, HALF_UP) (06-emission-calculation.md)
    public static BigDecimal computeEmission(BigDecimal quantity, BigDecimal factorValue) {
        if (quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("활동량은 0 이상이어야 합니다: " + quantity);
        }
        if (factorValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("배출계수는 0 이상이어야 합니다: " + factorValue);
        }
        return quantity.multiply(factorValue).setScale(6, RoundingMode.HALF_UP);
    }
}
