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

    @Nested
    class Cat11 {

        @Test
        void 생애주기_배출_연간_귀속_계산_정확도() {
            // TV 1,000대 × 0.5 tCO2e/대 ÷ 8년 = 62.500000 tCO2e/year
            BigDecimal result = Scope3Cat11Calculator.computeAnnualEmission(
                new BigDecimal("1000"), new BigDecimal("0.5"), 8);
            assertThat(result).isEqualByComparingTo(new BigDecimal("62.500000"));
        }

        @Test
        void 사용기간이_1년이면_전체_생애주기_배출_그대로() {
            BigDecimal result = Scope3Cat11Calculator.computeAnnualEmission(
                new BigDecimal("100"), new BigDecimal("2.0"), 1);
            assertThat(result).isEqualByComparingTo(new BigDecimal("200.000000"));
        }

        @Test
        void 사용기간_0이면_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("1000"), new BigDecimal("0.5"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용기간");
        }

        @Test
        void 사용기간_null이면_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("1000"), new BigDecimal("0.5"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용기간");
        }

        @Test
        void 음수_판매량은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("-10"), new BigDecimal("0.5"), 5))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CoverageCalculator {

        private final java.util.UUID TENANT_ID = java.util.UUID.randomUUID();
        private final java.util.UUID ENTITY_ID = java.util.UUID.randomUUID();

        @Test
        void 포함_배출_비율_95_이상이면_threshold_충족() {
            // Cat1=800, Cat2=150 → includedTotal=950, estimatedExcluded=50 → 95.00%
            var included = java.util.Map.of(1, new BigDecimal("800"), 2, new BigDecimal("150"));
            var excluded  = java.util.Map.of(4, new BigDecimal("50"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                included, excluded, java.util.Map.of(4, "중요성 낮음"));

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("95.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
            assertThat(report.includedCategories()).containsExactlyInAnyOrder(1, 2);
            assertThat(report.excludedCategories()).containsExactlyInAnyOrder(4);
        }

        @Test
        void 포함_배출_비율_95_미만이면_threshold_미달() {
            // Cat1=700, estimatedExcluded Cat4=300 → 70.00%
            var included = java.util.Map.of(1, new BigDecimal("700"));
            var excluded  = java.util.Map.of(4, new BigDecimal("300"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025, included, excluded, java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("70.00"));
            assertThat(report.meets95PctThreshold()).isFalse();
        }

        @Test
        void 제외_추정치_없으면_100퍼센트_달성() {
            var included = java.util.Map.of(1, new BigDecimal("500"), 2, new BigDecimal("200"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                included, java.util.Map.of(), java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
        }

        @Test
        void 포함과_제외_모두_비어있으면_100퍼센트() {
            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
        }
    }
}
