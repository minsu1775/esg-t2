package ai.claudecode.esgt2.rpt.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ReportBuilderTest {

    // T-7-02: KSSB2 보고서 생성 시 Scope1·2·3 섹션 포함
    @Test
    void KSSB2_보고서에_Scope1_2_3_섹션이_포함된다() {
        var sections = ReportBuilder.buildKssb2Sections(
            Map.of(
                "SCOPE1",    BigDecimal.valueOf(100),
                "SCOPE2_LB", BigDecimal.valueOf(50),
                "SCOPE2_MB", BigDecimal.valueOf(45),
                "SCOPE3",    BigDecimal.valueOf(200)
            ),
            null  // 전년 데이터 없음
        );

        var codes = sections.stream().map(ReportSection::itemCode).toList();
        assertThat(codes).contains("KSSB2.S1", "KSSB2.S2-LB", "KSSB2.S2-MB", "KSSB2.S3");
    }

    @Test
    void 전년_데이터_없을_때_YoY_null() {
        var sections = ReportBuilder.buildKssb2Sections(
            Map.of("SCOPE1", BigDecimal.valueOf(100)),
            null
        );
        var s1 = sections.stream()
            .filter(s -> "KSSB2.S1".equals(s.itemCode())).findFirst().orElseThrow();
        assertThat(s1.yoyDelta()).isNull();
    }

    @Test
    void 전년_데이터_있을_때_YoY_계산() {
        var prevYearData = Map.of("SCOPE1", BigDecimal.valueOf(80.0));
        var sections = ReportBuilder.buildKssb2Sections(
            Map.of("SCOPE1", BigDecimal.valueOf(100.0)),
            prevYearData
        );
        var s1 = sections.stream()
            .filter(s -> "KSSB2.S1".equals(s.itemCode())).findFirst().orElseThrow();
        // (100 - 80) / 80 * 100 = 25%
        assertThat(s1.yoyDelta()).isEqualByComparingTo(new java.math.BigDecimal("25.00"));
    }
}
