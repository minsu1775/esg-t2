package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.ghg.domain.formula.FormulaLoader;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FormulaLoader test_cases 게이트 유닛 테스트 (T-6B-07).
 * FormulaLoader는 Phase 5에서 이미 구현됨 — 여기서는 정정·Formula DSL 관점에서 재검증.
 */
class FormulaVersionDomainTest {

    private static final String VALID_YAML = """
        formula:
          code: EM-TEST-001
          version: "1.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 56.0
            - inputs: { fuel: 0.0, ef: 0.056 }
              expected: 0.0
        """;

    private static final String EMPTY_TEST_CASES_YAML = """
        formula:
          code: EM-TEST-002
          version: "1.0"
          expression: "fuel * ef"
          test_cases: []
        """;

    private static final String FAILING_TEST_CASE_YAML = """
        formula:
          code: EM-TEST-003
          version: "1.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 999.0
        """;

    // T-6B-07: 유효한 YAML + test_cases 모두 통과 → true 반환
    @Test
    void 유효한_YAML_test_cases_모두_통과() {
        assertThat(FormulaLoader.validate(VALID_YAML)).isTrue();
    }

    // T-6B-07: test_cases 비어있으면 활성화 차단
    @Test
    void test_cases_비어있으면_FormulaValidationException() {
        assertThatThrownBy(() -> FormulaLoader.validate(EMPTY_TEST_CASES_YAML))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases");
    }

    // T-6B-07: test_cases 결과 불일치 → 차단
    @Test
    void test_cases_결과_불일치_시_FormulaValidationException() {
        assertThatThrownBy(() -> FormulaLoader.validate(FAILING_TEST_CASE_YAML))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases 불일치");
    }
}
