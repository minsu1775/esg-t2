package ai.claudecode.esgt2.rpt.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KSSB 2 공시 보고서 섹션 조립기.
 * GHG 스코프별 배출량 맵 → ReportSection 목록.
 */
public final class ReportBuilder {

    private ReportBuilder() {}

    /**
     * KSSB 2 기준 섹션 목록 생성.
     *
     * @param current  현재 연도 배출량 맵 (scope → tCO2e)
     * @param previous 전년 배출량 맵 (null = 전년 데이터 없음)
     */
    public static List<ReportSection> buildKssb2Sections(
            Map<String, BigDecimal> current,
            Map<String, BigDecimal> previous) {

        var sections = new ArrayList<ReportSection>();

        BigDecimal scope1   = current.getOrDefault("SCOPE1",    BigDecimal.ZERO);
        BigDecimal scope2Lb = current.getOrDefault("SCOPE2_LB", BigDecimal.ZERO);
        BigDecimal scope2Mb = current.getOrDefault("SCOPE2_MB", BigDecimal.ZERO);
        BigDecimal scope3   = current.getOrDefault("SCOPE3",    BigDecimal.ZERO);

        BigDecimal prevScope1   = previous == null ? null : previous.get("SCOPE1");
        BigDecimal prevScope2Lb = previous == null ? null : previous.get("SCOPE2_LB");
        BigDecimal prevScope2Mb = previous == null ? null : previous.get("SCOPE2_MB");
        BigDecimal prevScope3   = previous == null ? null : previous.get("SCOPE3");

        sections.add(new ReportSection("KSSB2.S1",    "Scope 1 직접 배출량",
            scope1,   YoyCalculator.delta(scope1,   prevScope1)));
        sections.add(new ReportSection("KSSB2.S2-LB", "Scope 2 간접 배출량 (위치 기반)",
            scope2Lb, YoyCalculator.delta(scope2Lb, prevScope2Lb)));
        sections.add(new ReportSection("KSSB2.S2-MB", "Scope 2 간접 배출량 (시장 기반)",
            scope2Mb, YoyCalculator.delta(scope2Mb, prevScope2Mb)));
        sections.add(new ReportSection("KSSB2.S3",    "Scope 3 기타 간접 배출량",
            scope3,   YoyCalculator.delta(scope3,   prevScope3)));

        return sections;
    }
}
