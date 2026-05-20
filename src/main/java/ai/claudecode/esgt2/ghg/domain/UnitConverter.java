package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class UnitConverter {

    private static final Map<String, BigDecimal> FACTORS = Map.ofEntries(
        Map.entry("GJ->kWh",   new BigDecimal("277.7777777778")),
        Map.entry("kWh->GJ",   new BigDecimal("0.0036")),
        Map.entry("TJ->GJ",    new BigDecimal("1000")),
        Map.entry("GJ->TJ",    new BigDecimal("0.001")),
        Map.entry("Mcal->GJ",  new BigDecimal("0.0041868")),
        Map.entry("GJ->Mcal",  new BigDecimal("238.8458966")),
        Map.entry("MWh->GJ",   new BigDecimal("3.6")),
        Map.entry("GJ->MWh",   new BigDecimal("0.2777777778")),
        Map.entry("ton->kg",   new BigDecimal("1000")),
        Map.entry("kg->ton",   new BigDecimal("0.001")),
        Map.entry("kL->L",     new BigDecimal("1000")),
        Map.entry("L->kL",     new BigDecimal("0.001"))
    );

    // 단위별 기준 단위 매핑 (에너지→GJ, 부피→kL, 질량→ton)
    private static final Map<String, String> STANDARD_UNITS = Map.of(
        "GJ", "GJ", "TJ", "GJ", "kWh", "GJ", "MWh", "GJ", "Mcal", "GJ",
        "kL", "kL", "L", "kL",
        "ton", "ton", "kg", "ton"
    );

    private UnitConverter() {}

    /** 입력 단위에 대한 기준 단위를 반환. 알 수 없는 단위는 null. */
    public static String standardUnitFor(String inputUnit) {
        return STANDARD_UNITS.get(inputUnit);
    }

    public static BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
        if (fromUnit.equals(toUnit)) {
            return quantity.setScale(6, RoundingMode.HALF_UP);
        }
        String key = fromUnit + "->" + toUnit;
        BigDecimal factor = FACTORS.get(key);
        if (factor == null) {
            throw new IllegalArgumentException(
                "지원하지 않는 단위 변환: " + fromUnit + " → " + toUnit);
        }
        return quantity.multiply(factor).setScale(6, RoundingMode.HALF_UP);
    }
}
