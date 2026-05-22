package ai.claudecode.esgt2.rpt;

import ai.claudecode.esgt2.rpt.api.CreateReportRequest;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 공시 보고서 통합 테스트 (T-7-01~09).
 */
class ReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000095");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000096");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        // FK 역순으로 정리
        jdbcTemplate.execute(
            "DELETE FROM disclosure_reports WHERE tenant_id = '00000000-0000-0000-0000-000000000095'");
        jdbcTemplate.execute(
            "DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000095'");
        jdbcTemplate.execute(
            "DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000095'");
        jdbcTemplate.execute(
            "DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000095'");

        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000095','RPT95','보고서테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000096','00000000-0000-0000-0000-000000000095'," +
            "'보고서법인','KR','SUBSIDIARY') ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER", "TENANT_ADMIN")));
    }

    // T-7-01, T-7-05: 보고서 생성 + DRAFT 상태
    @Test
    void 보고서_생성_DRAFT_상태로_시작() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));

        assertThat(report.id()).isNotNull();
        assertThat(report.status()).isEqualTo("DRAFT");
        assertThat(report.framework()).isEqualTo("KSSB2");
        assertThat(report.sections()).isNotEmpty();
    }

    // T-7-06: KSSB2 섹션 포함 확인
    @Test
    void KSSB2_보고서_섹션_코드_포함() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));

        var codes = report.sections().stream().map(s -> s.itemCode()).toList();
        assertThat(codes).contains("KSSB2.S1", "KSSB2.S2-LB", "KSSB2.S2-MB", "KSSB2.S3");
    }

    // T-7-08: 승인 워크플로우 DRAFT → SUBMITTED → APPROVED
    @Test
    void 승인_워크플로우_DRAFT_SUBMITTED_APPROVED() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));

        var submitted = reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        assertThat(submitted.status()).isEqualTo("SUBMITTED");

        var approved = reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
    }

    // T-7-04: DRAFT 보고서 → isApproved() false (vw 모듈 게이트)
    @Test
    void DRAFT_보고서는_isApproved_false() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));

        assertThat(reportService.isApproved(TENANT_ID, report.id())).isFalse();
    }

    // T-7-12: reject reason 공백 → 예외
    @Test
    void 반려_사유_누락_시_예외() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());

        assertThatThrownBy(() ->
            reportService.rejectReport(TENANT_ID, ACTOR_ID, report.id(), ""))
            .isInstanceOf(EsgException.class);
    }

    // T-7-09: PDF 생성 확인
    @Test
    void PDF_생성_바이트_배열_반환() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));

        byte[] pdf = reportService.generatePdf(TENANT_ID, report.id());

        assertThat(pdf).isNotEmpty();
        // PDF 매직 바이트 확인: %PDF
        assertThat(pdf[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(pdf[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(pdf[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(pdf[3]).isEqualTo((byte) 0x46); // 'F'
    }
}
