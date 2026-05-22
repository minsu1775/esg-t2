package ai.claudecode.esgt2.rpt.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class YoyCalculatorTest {

    // T-7-03: 전년 데이터 없으면 null (N/A)
    @Test
    void 전년_데이터_없으면_null_반환() {
        assertThat(YoyCalculator.delta(BigDecimal.valueOf(100), null)).isNull();
    }

    @Test
    void 전년_데이터_0이면_null_반환_제로나누기_방지() {
        assertThat(YoyCalculator.delta(BigDecimal.valueOf(100), BigDecimal.ZERO)).isNull();
    }

    @Test
    void 증가율_정상_계산() {
        // (120 - 100) / 100 * 100 = 20.00%
        var result = YoyCalculator.delta(BigDecimal.valueOf(120), BigDecimal.valueOf(100));
        assertThat(result).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void 감소율_정상_계산() {
        // (80 - 100) / 100 * 100 = -20.00%
        var result = YoyCalculator.delta(BigDecimal.valueOf(80), BigDecimal.valueOf(100));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-20.00"));
    }
}
