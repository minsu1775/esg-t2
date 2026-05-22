package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.api.FormulaVersionService;
import ai.claudecode.esgt2.ghg.api.RegisterFormulaRequest;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Formula 버전 관리 통합 테스트 (T-6B-07, T-6B-08, T-6B-09).
 */
class FormulaVersionIntegrationTest extends AbstractIntegrationTest {

    @Autowired FormulaVersionService formulaVersionService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000090");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String VALID_YAML = """
        formula:
          code: EM-SCOPE1-FUEL
          version: "2.0"
          ghg_category: SCOPE1_FUEL
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 56.0
        """;

    private static final String FAILING_YAML = """
        formula:
          code: EM-SCOPE1-FUEL
          version: "3.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 9999.0
        """;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM formula_versions WHERE code LIKE 'EM-SCOPE1%'");
        jdbcTemplate.execute(
            "DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000090'");

        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000090','FORM90','포뮬라테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000091','00000000-0000-0000-0000-000000000090'," +
            "'포뮬라법인','KR','SUBSIDIARY') " +
            "ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
    }

    // T-6B-07, T-6B-08: test_cases 통과 → 등록 성공 (ACTIVE)
    @Test
    void test_cases_통과_산식_등록_성공() {
        var response = formulaVersionService.register(ACTOR_ID,
            new RegisterFormulaRequest(VALID_YAML));

        assertThat(response.code()).isEqualTo("EM-SCOPE1-FUEL");
        assertThat(response.version()).isEqualTo("2.0");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.ghgCategory()).isEqualTo("SCOPE1_FUEL");
    }

    // T-6B-07: test_cases 실패 → 활성화 차단 (FormulaValidationException)
    @Test
    void test_cases_실패_산식_등록_차단() {
        assertThatThrownBy(() ->
            formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(FAILING_YAML)))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases");
    }

    // T-6B-08: 신규 버전 등록 시 이전 ACTIVE 버전 자동 INACTIVE
    @Test
    void 신규_버전_등록_시_이전_ACTIVE_비활성화() {
        formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(VALID_YAML));

        String updatedYaml = VALID_YAML.replace("version: \"2.0\"", "version: \"2.1\"");
        formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(updatedYaml));

        var versions = formulaVersionService.findAll("EM-SCOPE1-FUEL");
        assertThat(versions).hasSize(2);
        assertThat(versions.stream().filter(v -> "ACTIVE".equals(v.status())).count()).isEqualTo(1);
        assertThat(versions.stream().filter(v -> "INACTIVE".equals(v.status())).count()).isEqualTo(1);
    }

    // T-6B-09: 영향 조회 — ghgCategory에 해당하는 활동 데이터 반환
    @Test
    void 산식_변경_영향_활동데이터_조회() {
        var formula = formulaVersionService.register(ACTOR_ID,
            new RegisterFormulaRequest(VALID_YAML));

        // SCOPE1_FUEL 카테고리 활동 데이터 직접 삽입
        jdbcTemplate.execute(
            "INSERT INTO activity_data (id, tenant_id, entity_id, reporting_year, category, " +
            "sub_category, quantity, unit, country_code, data_source, data_quality, status, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000090', " +
            "'00000000-0000-0000-0000-000000000091', 2025, 'SCOPE1_FUEL', 'GAS', " +
            "1000.0, 'GJ', 'KR', 'MANUAL', 'AVERAGE_DATA', 'DRAFT', NOW(), NOW())");

        var impact = formulaVersionService.getImpact(TENANT_ID, formula.id());

        assertThat(impact.formulaCode()).isEqualTo("EM-SCOPE1-FUEL");
        assertThat(impact.ghgCategory()).isEqualTo("SCOPE1_FUEL");
        assertThat(impact.affectedActivityDataCount()).isEqualTo(1L);
        assertThat(impact.affectedEntityIds()).containsExactly(ENTITY_ID);
    }
}
