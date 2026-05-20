package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Scope3CalculatorTest {

    @Nested
    class Cat1 {

        @Test
        void 지출기반_CO2e_계산_정확도() {
            // 10,000 KRW × 0.0005 tCO2e/KRW = 5.000000 tCO2e
            BigDecimal result = Scope3Cat1Calculator.computeEmission(
                new BigDecimal("10000"), new BigDecimal("0.0005"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("5.000000"));
        }

        @Test
        void SUPPLIER_PORTAL_출처는_SUPPLIER_SPECIFIC_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("SUPPLIER_PORTAL"))
                .isEqualTo("SUPPLIER_SPECIFIC");
        }

        @Test
        void API_출처는_AVERAGE_DATA_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("API"))
                .isEqualTo("AVERAGE_DATA");
        }

        @Test
        void MANUAL_출처는_SPEND_BASED_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("MANUAL"))
                .isEqualTo("SPEND_BASED");
        }

        @Test
        void CSV_UPLOAD_출처는_SPEND_BASED_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("CSV_UPLOAD"))
                .isEqualTo("SPEND_BASED");
        }

        @Test
        void 음수_지출액은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat1Calculator.computeEmission(new BigDecimal("-1"), new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Cat2 {

        @Test
        void 자본재_취득액_CO2e_계산_정확도() {
            // 500,000 KRW × 0.0003 tCO2e/KRW = 150.000000 tCO2e
            BigDecimal result = Scope3Cat2Calculator.computeEmission(
                new BigDecimal("500000"), new BigDecimal("0.0003"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("150.000000"));
        }

        @Test
        void 음수_취득액은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat2Calculator.computeEmission(new BigDecimal("-100"), new BigDecimal("0.3")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
