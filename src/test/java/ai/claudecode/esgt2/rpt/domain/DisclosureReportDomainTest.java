package ai.claudecode.esgt2.rpt.domain;

import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class DisclosureReportDomainTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();

    private DisclosureReport makeReport() {
        return DisclosureReport.create(new CreateReportCommand(
            TENANT_ID, ENTITY_ID, 2025, "KSSB2",
            Map.of("SCOPE1", BigDecimal.valueOf(100),
                   "SCOPE2_LB", BigDecimal.valueOf(50),
                   "SCOPE3", BigDecimal.valueOf(200))
        ));
    }

    // T-7-11: 상태 전이는 명시적 메서드만 허용
    @Test
    void 신규_보고서는_DRAFT_상태() {
        assertThat(makeReport().status()).isEqualTo("DRAFT");
    }

    @Test
    void DRAFT_submit_호출_시_SUBMITTED_전이() {
        var report = makeReport();
        report.submit(ACTOR_ID);
        assertThat(report.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void SUBMITTED_approve_호출_시_APPROVED_전이() {
        var report = makeReport();
        report.submit(ACTOR_ID);
        report.approve(ACTOR_ID);
        assertThat(report.status()).isEqualTo("APPROVED");
    }

    // T-7-12: reject reason 공백 → EsgException
    @Test
    void reject_reason_공백_시_예외() {
        var report = makeReport();
        report.submit(ACTOR_ID);
        assertThatThrownBy(() -> report.reject(ACTOR_ID, ""))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void reject_reason_null_시_예외() {
        var report = makeReport();
        report.submit(ACTOR_ID);
        assertThatThrownBy(() -> report.reject(ACTOR_ID, null))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void DRAFT_상태에서_approve_호출_시_예외() {
        var report = makeReport();
        assertThatThrownBy(() -> report.approve(ACTOR_ID))
            .isInstanceOf(EsgException.class);
    }

    // T-7-02: 보고서 수치 정확도
    @Test
    void 보고서_수치_Scope123_합산_정확도() {
        var report = makeReport();
        var total = report.totalEmission();
        // 100 + 50 + 200 = 350
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(350));
    }
}
