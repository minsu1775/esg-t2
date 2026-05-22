package ai.claudecode.esgt2.rpt.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * YoY(전년 대비) 증감률 계산기.
 * 전년 데이터 없거나 0이면 null(N/A) 반환.
 */
public final class YoyCalculator {

    private YoyCalculator() {}

    /**
     * @param current  현재 연도 값
     * @param previous 전년 값 (null 가능)
     * @return 증감률(%) 소수점 2자리, 전년 없으면 null
     */
    public static BigDecimal delta(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
        return current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
