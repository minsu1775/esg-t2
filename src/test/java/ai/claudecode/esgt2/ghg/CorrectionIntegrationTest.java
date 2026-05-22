package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.api.ActivityDataVersionResponse;
import ai.claudecode.esgt2.ghg.api.CorrectActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정정 워크플로우 통합 테스트 (T-6B-01~06).
 * TestContainers PostgreSQL 위에서 실제 Flyway 마이그레이션 후 실행.
 */
class CorrectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired GhgService ghgService;
    @Autowired ActivityDataRepository activityDataRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000088");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000089");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");
        jdbcTemplate.execute(
            "DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");
        jdbcTemplate.execute(
            "DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");

        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000088','CORR88','정정테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000089','00000000-0000-0000-0000-000000000088'," +
            "'정정법인','KR','SUBSIDIARY') " +
            "ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
    }

    // T-6B-01: 정정 후 원본 ARCHIVED, 새 레코드 생성 — 원본 quantity 불변 (P1 재현성)
    @Test
    void 정정_후_원본_ARCHIVED_새_레코드_생성_원본_불변() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));

        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null,
                "계량기 재측정"));

        // 정정 레코드 확인 — 새 UUID 생성됨
        assertThat(corrected.id()).isNotEqualTo(original.id());
        assertThat(corrected.quantity()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(corrected.status()).isEqualTo("DRAFT");

        // 원본 ARCHIVED 확인 (P1: status만 변경, 값은 불변)
        var originalEntity = activityDataRepository
            .findByIdAndTenantId(original.id(), TENANT_ID).orElseThrow();
        assertThat(originalEntity.getStatus()).isEqualTo("ARCHIVED");
        assertThat(originalEntity.getQuantity()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    // T-6B-05: 버전 이력 조회 — 원본 + 정정본 모두 포함, createdAt 정렬
    @Test
    void 버전_이력_조회_원본과_정정본_모두_반환() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "재측정"));

        List<ActivityDataVersionResponse> history =
            ghgService.findVersionHistory(TENANT_ID, original.id());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(original.id());
        assertThat(history.get(1).id()).isEqualTo(corrected.id());
        assertThat(history.get(1).correctionOf()).isEqualTo(original.id());
    }

    // T-6B-06: diff 조회 — 정정 전·후 수치 비교
    @Test
    void diff_조회_정정전후_수치_비교() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "재측정"));

        var diff = ghgService.findDiff(TENANT_ID, corrected.id());

        assertThat(diff.originalId()).isEqualTo(original.id());
        assertThat(diff.correctedId()).isEqualTo(corrected.id());
        assertThat(diff.originalQuantity()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(diff.correctedQuantity()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(diff.correctionReason()).isEqualTo("재측정");
    }

    // T-6B-02: 정정 사유 누락 → EsgException (VALIDATION_FAILED)
    @Test
    void 정정_사유_누락_시_예외() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));

        assertThatThrownBy(() -> ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "")))
            .isInstanceOf(EsgException.class)
            .hasMessageContaining("정정 사유");
    }
}
