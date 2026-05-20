package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.audit.internal.OutboxProcessingService;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageRequest;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageResponse;
import ai.claudecode.esgt2.ghg.api.Scope3Service;
import ai.claudecode.esgt2.ghg.infra.EmissionFactorLoader;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Scope3IntegrationTest extends AbstractIntegrationTest {

    @Autowired private Scope3Service scope3Service;
    @Autowired private GhgService ghgService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private EmissionFactorLoader emissionFactorLoader;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessingService outboxProcessingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityId;

    @BeforeEach
    void setup() throws Exception {
        // 테스트 데이터 정리 (FK 순서 역순)
        jdbcTemplate.execute("DELETE FROM scope3_coverage_reports WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        outboxEventRepository.deleteAll();
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000004', 'TEST4', '테스트법인4', 'KR') " +
            "ON CONFLICT DO NOTHING");

        // JWT 보안 컨텍스트 설정 (@Auditable AOP가 tenantId/actorId를 읽어야 함)
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));

        // Scope3 배출계수 로드
        emissionFactorLoader.loadFile(new ClassPathResource("factors/scope3-test-factors.yaml"));

        // 법인 생성
        var entityResp = entityManagementService.create(
            TENANT_ID, new CreateEntityRequest("테스트법인4", "KR", LegalEntityType.PARENT));
        entityId = entityResp.id();
    }

    @Test
    void Cat1_지출기반_배출량_산출_후_emission_record_저장() {
        // Cat.1 활동 데이터 등록 (지출 10,000 KRW, 배출계수 0.0005 tCO2e/KRW)
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("10000"), "KRW", "KR", "MANUAL", null, null));

        var results = scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("5.000000"));
        assertThat(results.get(0).scope()).isEqualTo("SCOPE3");

        // AuditLog 확인
        outboxProcessingService.processNow();
        int logCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE event_type = 'SCOPE3_CAT1_CALCULATED'", Integer.class);
        assertThat(logCount).isGreaterThan(0);
    }

    @Test
    void Cat2_자본재_배출량_산출_후_emission_record_저장() {
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT2", null,
            new BigDecimal("500000"), "KRW", "KR", "MANUAL", null, null));

        var results = scope3Service.calculateCat2(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("150.000000"));
    }

    @Test
    void Cat11_생애주기_귀속_배출량_산출() {
        // TV 1,000대, 배출계수 0.5 tCO2e/대, 사용기간 8년 → 62.500000 tCO2e/year
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT11", "TV",
            new BigDecimal("1000"), "units", "KR", "MANUAL", null, 8));

        var results = scope3Service.calculateCat11(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("62.500000"));
    }

    @Test
    void 커버리지_보고서_생성_후_95퍼센트_달성_확인() {
        // Cat.1 배출량 등록 후 산출 (10,000 KRW × 0.0005 = 5 tCO2e)
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("10000"), "KRW", "KR", "MANUAL", null, null));
        scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        // Cat.4 추정 제외 배출량 0.263158 → included/(included+excluded) = 5/(5.263158) ≈ 95.00%
        var request = new Scope3CoverageRequest(2025,
            Map.of(4, new BigDecimal("0.263158")),
            Map.of(4, "중요성 평가 결과 제외"));

        Scope3CoverageResponse response = scope3Service.generateCoverageReport(
            TENANT_ID, entityId, 2025, request);

        assertThat(response.meets95PctThreshold()).isTrue();
        assertThat(response.includedCategories()).contains(1);
        assertThat(response.excludedCategories()).contains(4);

        // 조회 테스트
        Scope3CoverageResponse fetched = scope3Service.getCoverageReport(TENANT_ID, entityId, 2025);
        assertThat(fetched.id()).isEqualTo(response.id());
    }

    @Test
    void 커버리지_보고서_미달_시_meets_threshold_false() {
        // Cat.1 배출량 700, 추정 제외 300 → 70%
        // 700 tCO2e을 위해: 700/0.0005 = 1,400,000 KRW
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("1400000"), "KRW", "KR", "MANUAL", null, null));
        scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        var request = new Scope3CoverageRequest(2025,
            Map.of(4, new BigDecimal("300")),
            Map.of(4, "데이터 없음"));

        Scope3CoverageResponse response = scope3Service.generateCoverageReport(
            TENANT_ID, entityId, 2025, request);

        assertThat(response.meets95PctThreshold()).isFalse();
    }
}
