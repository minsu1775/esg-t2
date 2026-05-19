package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmissionCalculatorTest {

    @Test
    void Scope1_연료연소_경유_1kL_CO2e_환산() {
        // KEEI 2025: 경유 2.596 tCO2e/kL
        BigDecimal result = EmissionCalculator.computeEmission(
            new BigDecimal("1.0"),
            new BigDecimal("2.596")
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.596000"));
    }

    @Test
    void Scope1_연료연소_소수점_정밀도_6자리() {
        // 1.5 kL × 2.596 = 3.894000
        BigDecimal result = EmissionCalculator.computeEmission(
            new BigDecimal("1.5"),
            new BigDecimal("2.596")
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("3.894000"));
        assertThat(result.scale()).isEqualTo(6);
    }

    @Test
    void Scope2_location_based_전력소비_100MWh() {
        // KEEI 2025: KR 전력망 0.4156 tCO2e/MWh, 100 MWh
        BigDecimal result = EmissionCalculator.computeEmission(
            new BigDecimal("100"),
            new BigDecimal("0.4156")
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("41.560000"));
    }

    @Test
    void Scope2_market_based_RE인증서_차감_적용() {
        // 전체 소비 200 MWh, RE 인증서 50 MWh → 순소비 150 MWh × 0.4156
        BigDecimal totalConsumption = new BigDecimal("200");
        BigDecimal reCertificate = new BigDecimal("50");
        BigDecimal netConsumption = totalConsumption.subtract(reCertificate);

        BigDecimal result = EmissionCalculator.computeEmission(netConsumption, new BigDecimal("0.4156"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("62.340000"));
    }

    @Test
    void 수량_0인_경우_배출량_0() {
        BigDecimal result = EmissionCalculator.computeEmission(
            BigDecimal.ZERO,
            new BigDecimal("2.596")
        );
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 수량_음수_허용_안됨() {
        assertThatThrownBy(() ->
            EmissionCalculator.computeEmission(
                new BigDecimal("-1"),
                new BigDecimal("2.596")
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 계수_음수_허용_안됨() {
        assertThatThrownBy(() ->
            EmissionCalculator.computeEmission(
                new BigDecimal("10"),
                new BigDecimal("-1")
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
