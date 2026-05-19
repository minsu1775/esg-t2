package ai.claudecode.esgt2.ghg.domain;

import java.time.LocalDate;

public interface EmissionFactorResolver {

    // 산출 시점의 배출계수 조회 — 과거 공시 재현성 보장 (L-0-09, 06-emission-calculation.md)
    EmissionFactor resolveAt(String category, String subCategory, String countryCode, LocalDate date);
}
