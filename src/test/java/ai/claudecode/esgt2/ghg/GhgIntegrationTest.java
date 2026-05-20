package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.audit.internal.OutboxProcessingService;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.infra.EmissionFactorLoader;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GhgIntegrationTest extends AbstractIntegrationTest {

    @Autowired private GhgService ghgService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private EmissionFactorLoader emissionFactorLoader;
    @Autowired private EmissionRecordRepository emissionRecordRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxProcessingService outboxProcessingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private UUID entityId;

    @BeforeEach
    void setup() {
        // FK 순서: emission_records/activity_data → entity_relationships → legal_entities
        jdbcTemplate.execute("DELETE FROM emission_records");
        jdbcTemplate.execute("DELETE FROM activity_data");
        jdbcTemplate.execute("DELETE FROM entity_relationships");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000001'");
        jdbcTemplate.execute("DELETE FROM emission_factors");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        outboxEventRepository.deleteAll();

        var auth = new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        emissionFactorLoader.loadFile(new ClassPathResource("emission-factors/keei-2025.yaml"));

        var entityRequest = new CreateEntityRequest("GHG테스트법인", "KR", LegalEntityType.PARENT);
        var entityAuth = new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(entityAuth);
        entityId = entityManagementService.create(TENANT_ID, entityRequest).id();

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void 활동데이터_등록_성공() {
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("100"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);

        ActivityDataResponse response = ghgService.createActivityData(TENANT_ID, entityId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.category()).isEqualTo("SCOPE1_FUEL");
        assertThat(response.quantity()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void 활동데이터_등록_시_감사로그_생성() {
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("50"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);

        outboxProcessingService.processNow();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?",
            Long.class, TENANT_ID)).isGreaterThan(0);
    }

    @Test
    void Scope1_배출량_산출_경유_100kL() {
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("100"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);

        List<EmissionRecordResponse> records = ghgService.calculateEmissions(TENANT_ID, entityId, 2025);

        assertThat(records).hasSize(1);
        // 100 kL × 2.596 tCO2e/kL = 259.600000 tCO2e (06-emission-calculation.md: scale=6, HALF_UP)
        assertThat(records.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("259.600000"));
        assertThat(records.get(0).scope()).isEqualTo("SCOPE1");
        assertThat(records.get(0).ghgType()).isEqualTo("CO2E");
        assertThat(records.get(0).emissionFactorId()).isNotNull();
    }

    @Test
    void Scope2_배출량_산출_전력_1000MWh() {
        var request = new CreateActivityDataRequest(2025, "SCOPE2_ELECTRICITY", "GRID",
            new BigDecimal("1000"), "MWh", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);

        List<EmissionRecordResponse> records = ghgService.calculateEmissions(TENANT_ID, entityId, 2025);

        assertThat(records).hasSize(1);
        // 1000 MWh × 0.4156 tCO2e/MWh = 415.600000
        assertThat(records.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("415.600000"));
        assertThat(records.get(0).scope()).isEqualTo("SCOPE2_LB");
    }

    @Test
    void 배출량_기록_조회_성공() {
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("50"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);
        ghgService.calculateEmissions(TENANT_ID, entityId, 2025);

        List<EmissionRecordResponse> records = ghgService.findEmissionRecords(TENANT_ID, entityId, 2025);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).entityId()).isEqualTo(entityId);
        assertThat(records.get(0).reportingYear()).isEqualTo(2025);
    }

    @Test
    void 배출량_기록은_INSERT_only_append_only_보장() {
        // EmissionRecord는 P1 원칙 — 저장 후 값 변경 불가
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("100"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);
        ghgService.calculateEmissions(TENANT_ID, entityId, 2025);

        long countBefore = emissionRecordRepository.findByTenantIdAndEntityIdAndReportingYear(
            TENANT_ID, entityId, 2025).size();

        // 재산출 → 기존 기록 삭제 없이 새 기록 추가 (append-only)
        ghgService.calculateEmissions(TENANT_ID, entityId, 2025);
        long countAfter = emissionRecordRepository.findByTenantIdAndEntityIdAndReportingYear(
            TENANT_ID, entityId, 2025).size();

        assertThat(countAfter).isEqualTo(countBefore + 1);
    }

    @Test
    void BigDecimal_전용_산출_확인_소수점_6자리() {
        var request = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("1"), "kL", "KR", "MANUAL", "AVERAGE_DATA", null);
        ghgService.createActivityData(TENANT_ID, entityId, request);

        List<EmissionRecordResponse> records = ghgService.calculateEmissions(TENANT_ID, entityId, 2025);

        // 1 × 2.596 = 2.596000 — scale=6, no float/double rounding artifact
        assertThat(records.get(0).rawEmission().scale()).isEqualTo(6);
        assertThat(records.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("2.596000"));
    }
}
