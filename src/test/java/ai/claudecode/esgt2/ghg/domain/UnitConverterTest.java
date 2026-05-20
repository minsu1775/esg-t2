package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitConverterTest {

    @Test
    void GJ를_kWh로_변환() {
        // 1 GJ = 277.7778 kWh
        BigDecimal result = UnitConverter.convert(new BigDecimal("1"), "GJ", "kWh");
        assertThat(result).isEqualByComparingTo(new BigDecimal("277.777778"));
    }

    @Test
    void TJ를_GJ로_변환() {
        // 1 TJ = 1000 GJ
        BigDecimal result = UnitConverter.convert(new BigDecimal("2"), "TJ", "GJ");
        assertThat(result).isEqualByComparingTo(new BigDecimal("2000.000000"));
    }

    @Test
    void Mcal를_GJ로_변환() {
        // 1 Mcal = 0.0041868 GJ, 1000 Mcal = 4.1868 GJ
        BigDecimal result = UnitConverter.convert(new BigDecimal("1000"), "Mcal", "GJ");
        assertThat(result).isEqualByComparingTo(new BigDecimal("4.186800"));
    }

    @Test
    void MWh를_GJ로_변환() {
        // 1 MWh = 3.6 GJ
        BigDecimal result = UnitConverter.convert(new BigDecimal("10"), "MWh", "GJ");
        assertThat(result).isEqualByComparingTo(new BigDecimal("36.000000"));
    }

    @Test
    void 동일_단위_변환_계수_1() {
        BigDecimal result = UnitConverter.convert(new BigDecimal("100"), "kL", "kL");
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    @Test
    void 미지원_단위_변환_예외() {
        assertThatThrownBy(() ->
            UnitConverter.convert(new BigDecimal("1"), "BTU", "GJ")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("BTU");
    }

    @Test
    void 음수_수량_예외() {
        assertThatThrownBy(() ->
            UnitConverter.convert(new BigDecimal("-1"), "GJ", "kWh")
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
