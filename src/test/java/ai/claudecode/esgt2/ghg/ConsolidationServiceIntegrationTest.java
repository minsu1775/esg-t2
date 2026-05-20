package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.api.SetRelationshipRequest;
import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.ConsolidationResponse;
import ai.claudecode.esgt2.ghg.api.ConsolidationService;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.audit.internal.OutboxProcessingService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ConsolidationService consolidationService;
    @Autowired private GhgService ghgService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private EmissionFactorLoader emissionFactorLoader;
    @Autowired private OutboxProcessingService outboxProcessingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityA; // 루트
    private UUID entityB; // A의 60% 자회사
    private UUID entityC; // B의 70% 자회사 (A 기준 실질 소유율 42%)

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM consolidated_emission_contributions");
        jdbcTemplate.execute("DELETE FROM consolidated_emission_records");
        jdbcTemplate.execute("DELETE FROM emission_records");
        jdbcTemplate.execute("DELETE FROM activity_data");
        jdbcTemplate.execute("DELETE FROM entity_relationships");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000003'");
        jdbcTemplate.execute("DELETE FROM emission_factors");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000003', 'TEST3', '테스트법인3', 'KR') " +
            "ON CONFLICT DO NOTHING");

        var auth = new JwtAuthentication(ACTOR_ID, TENANT_ID,
            List.of("ESG_MANAGER", "TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        emissionFactorLoader.loadFile(new ClassPathResource("emission-factors/keei-2025.yaml"));

        entityA = entityManagementService.create(TENANT_ID,
            new CreateEntityRequest("법인A-루트", "KR", LegalEntityType.PARENT)).id();
        entityB = entityManagementService.create(TENANT_ID,
            new CreateEntityRequest("법인B-자회사", "KR", LegalEntityType.SUBSIDIARY)).id();
        entityC = entityManagementService.create(TENANT_ID,
            new CreateEntityRequest("법인C-손회사", "KR", LegalEntityType.SUBSIDIARY)).id();

        LocalDate relFrom = LocalDate.of(2025, 1, 1);
        entityManagementService.setRelationship(TENANT_ID, entityA,
            new SetRelationshipRequest(entityB, new BigDecimal("0.60"),
                ConsolidationMethod.EQUITY, relFrom, null));
        entityManagementService.setRelationship(TENANT_ID, entityB,
            new SetRelationshipRequest(entityC, new BigDecimal("0.70"),
                ConsolidationMethod.EQUITY, relFrom, null));

        // 각 법인: 경유 100kL → 259.600000 tCO2e
        var actReq = new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "DIESEL_AUTO",
            new BigDecimal("100"), "kL", "KR", "MANUAL", "AVERAGE_DATA");
        ghgService.createActivityData(TENANT_ID, entityA, actReq);
        ghgService.createActivityData(TENANT_ID, entityB, actReq);
        ghgService.createActivityData(TENANT_ID, entityC, actReq);

        ghgService.calculateEmissions(TENANT_ID, entityA, 2025);
        ghgService.calculateEmissions(TENANT_ID, entityB, 2025);
        ghgService.calculateEmissions(TENANT_ID, entityC, 2025);
    }

    @Test
    void Equity_Method_3법인_연결_집계() {
        // A: 259.6, B: 0.60×259.6=155.76, C: 0.42×259.6=109.032 → 합계 524.392
        ConsolidationResponse response = consolidationService.consolidate(
            TENANT_ID, entityA, 2025, "EQUITY");

        assertThat(response.id()).isNotNull();
        assertThat(response.rootEntityId()).isEqualTo(entityA);
        assertThat(response.reportingYear()).isEqualTo(2025);
        assertThat(response.consolidationMethod()).isEqualTo("EQUITY");
        assertThat(response.totalEmission()).isEqualByComparingTo(new BigDecimal("524.392000"));
        assertThat(response.contributions()).hasSize(3);
    }

    @Test
    void Operational_Control_42퍼_자회사_제외() {
        // A: 259.6 (루트), B: 60%>50% → 100% 포함 259.6, C: 42%≤50% → 제외
        // 합계 = 519.2
        ConsolidationResponse response = consolidationService.consolidate(
            TENANT_ID, entityA, 2025, "OPERATIONAL_CONTROL");

        assertThat(response.consolidationMethod()).isEqualTo("OPERATIONAL_CONTROL");
        assertThat(response.totalEmission()).isEqualByComparingTo(new BigDecimal("519.200000"));
        assertThat(response.contributions()).hasSize(2);
    }

    @Test
    void 연결_집계_이력_조회() {
        consolidationService.consolidate(TENANT_ID, entityA, 2025, "EQUITY");
        consolidationService.consolidate(TENANT_ID, entityA, 2025, "OPERATIONAL_CONTROL");

        List<ConsolidationResponse> history =
            consolidationService.findConsolidations(TENANT_ID, entityA, 2025);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(ConsolidationResponse::consolidationMethod)
            .containsExactlyInAnyOrder("EQUITY", "OPERATIONAL_CONTROL");
    }

    @Test
    void 연결_집계_산출_시_감사로그_생성() {
        consolidationService.consolidate(TENANT_ID, entityA, 2025, "EQUITY");
        outboxProcessingService.processNow();

        long auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?",
            Long.class, TENANT_ID);
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    void 기여분_상세_저장_확인() {
        ConsolidationResponse response = consolidationService.consolidate(
            TENANT_ID, entityA, 2025, "EQUITY");

        long contribCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM consolidated_emission_contributions WHERE consolidated_record_id = ?",
            Long.class, response.id());
        assertThat(contribCount).isEqualTo(3);
    }
}
