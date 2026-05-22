package ai.claudecode.esgt2.vw;

import ai.claudecode.esgt2.rpt.api.CreateReportRequest;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import ai.claudecode.esgt2.vw.api.SnapshotService;
import ai.claudecode.esgt2.vw.infra.VerificationSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 외부 검증 워크스페이스 통합 테스트 (T-8-03~10).
 */
class SnapshotIntegrationTest extends AbstractIntegrationTest {

    @Autowired SnapshotService snapshotService;
    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000097");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "DELETE FROM verification_signatures WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM verification_comments WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM verification_snapshots WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM disclosure_reports WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");

        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000097','VW97','검증테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000098','00000000-0000-0000-0000-000000000097'," +
            "'검증법인','KR','SUBSIDIARY') ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER", "TENANT_ADMIN")));
    }

    // T-8-05: 미승인(DRAFT) 보고서 → 스냅샷 생성 시도 → EsgException
    @Test
    void 미승인_보고서로_스냅샷_생성_시도_시_예외() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        // DRAFT 상태 — 아직 승인 안 됨

        assertThatThrownBy(() ->
            snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id()))
            .isInstanceOf(EsgException.class);
    }

    // T-8-03: 스냅샷 Repository는 append-only (delete 메서드 미노출)
    @Test
    void 스냅샷_Repository는_append_only_인터페이스이다() {
        var methods = Arrays.stream(VerificationSnapshotRepository.class.getMethods())
            .map(m -> m.getName())
            .toList();
        assertThat(methods).doesNotContain(
            "delete", "deleteById", "deleteAll",
            "deleteAllById", "deleteAllInBatch");
    }

    // T-8-03: 스냅샷 생성 후 해시 불변 확인
    @Test
    void 스냅샷_생성_후_hash_유지() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());

        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.snapshotHash()).hasSize(64);  // SHA-256 hex = 64자
        assertThat(snapshot.reportId()).isEqualTo(report.id());

        // 동일 ID로 다시 조회해도 hash 동일
        var snapshot2 = snapshotService.getSnapshot(TENANT_ID, snapshot.id());
        assertThat(snapshot2.snapshotHash()).isEqualTo(snapshot.snapshotHash());
    }

    // T-8-04: VERIFIER → 다른 snapshot_id 지정 → AccessDeniedException
    @Test
    void VERIFIER_다른_스냅샷_접근_시_AccessDenied() {
        // ESG_MANAGER로 APPROVED 보고서 + 스냅샷 생성
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        // VERIFIER 인증: 다른 (존재하지 않는) snapshot ID로 설정
        UUID otherSnapshotId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, null, otherSnapshotId,
                List.of("VERIFIER")));

        assertThatThrownBy(() ->
            snapshotService.getSnapshot(TENANT_ID, snapshot.id()))
            .isInstanceOf(AccessDeniedException.class);
    }

    // T-8-04 보완: VERIFIER → 지정된 스냅샷은 접근 허용
    @Test
    void VERIFIER_지정된_스냅샷은_접근_허용() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        // VERIFIER 인증: 정확한 snapshot ID 설정
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, null, snapshot.id(),
                List.of("VERIFIER")));

        assertThatCode(() ->
            snapshotService.getSnapshot(TENANT_ID, snapshot.id()))
            .doesNotThrowAnyException();
    }

    // T-8-09: 코멘트 작성 + 목록 조회
    @Test
    void 코멘트_작성_후_조회() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        snapshotService.addComment(TENANT_ID, ACTOR_ID, snapshot.id(), "Scope 1 데이터 확인 요청");

        var comments = snapshotService.listComments(TENANT_ID, snapshot.id());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).body()).isEqualTo("Scope 1 데이터 확인 요청");
    }

    // T-8-10: 서명 완료
    @Test
    void 검증_완료_서명() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        assertThat(snapshotService.isSigned(TENANT_ID, snapshot.id())).isFalse();

        snapshotService.signSnapshot(TENANT_ID, ACTOR_ID, snapshot.id(), "검증 완료 확인");

        assertThat(snapshotService.isSigned(TENANT_ID, snapshot.id())).isTrue();
    }
}
