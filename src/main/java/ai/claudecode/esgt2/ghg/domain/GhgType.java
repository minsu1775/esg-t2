package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public enum GhgType {
    CO2E(BigDecimal.ONE),       // 이미 CO2e로 환산된 값 (KEEI, DEFRA 계수)
    CO2(BigDecimal.ONE),        // GWP = 1
    CH4(new BigDecimal("27")),  // IPCC AR6 GWP100
    N2O(new BigDecimal("273")); // IPCC AR6 GWP100

    private final BigDecimal gwpAr6;

    GhgType(BigDecimal gwpAr6) {
        this.gwpAr6 = gwpAr6;
    }

    public BigDecimal getGwpAr6() {
        return gwpAr6;
    }
}
