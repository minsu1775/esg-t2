package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.ghg.domain.formula.FormulaConstants;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaLoader;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

class FormulaLoaderTest {

    @Test
    void test_cases_없는_수식_활성화_거부(/* T-3B-13 */) {
        String yaml = """
            formula:
              code: EM-TEST-001
              version: "1.0"
              expression: "x * ef"
              output_unit: tCO2e
              test_cases: []
            """;

        assertThatThrownBy(() -> FormulaLoader.validate(yaml))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases");
    }

    @Test
    void test_cases_통과시_활성화_허용() {
        String yaml = """
            formula:
              code: EM-TEST-002
              version: "1.0"
              expression: "quantity * ef"
              output_unit: tCO2e
              test_cases:
                - inputs: {quantity: 1.0, ef: 2.596}
                  expected: 2.596
            """;

        assertThat(FormulaLoader.validate(yaml)).isTrue();
    }

    @Test
    void test_cases_계산값_불일치_시_거부() {
        String yaml = """
            formula:
              code: EM-TEST-003
              version: "1.0"
              expression: "quantity * ef"
              output_unit: tCO2e
              test_cases:
                - inputs: {quantity: 1.0, ef: 2.596}
                  expected: 99.9
            """;

        assertThatThrownBy(() -> FormulaLoader.validate(yaml))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases");
    }

    @Test
    void EM_S1_FUEL_formula_yaml_test_cases_통과() throws Exception {
        String yaml = new ClassPathResource("formulas/EM-S1-FUEL.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(FormulaLoader.validate(yaml)).isTrue();
    }

    @Test
    void EM_S2_LB_formula_yaml_test_cases_통과() throws Exception {
        String yaml = new ClassPathResource("formulas/EM-S2-LB.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(FormulaLoader.validate(yaml)).isTrue();
    }

    @Test
    void EM_S2_MB_formula_yaml_test_cases_통과() throws Exception {
        String yaml = new ClassPathResource("formulas/EM-S2-MB.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(FormulaLoader.validate(yaml)).isTrue();
    }

    @Test
    void DoS_표현식_길이_초과_거부(/* T-3B-20 */) {
        String longExpr = "x * ".repeat(300); // > MAX_EXPRESSION_LENGTH
        String yaml = """
            formula:
              code: EM-DOS-001
              version: "1.0"
              expression: "%s"
              output_unit: tCO2e
              test_cases:
                - inputs: {x: 1.0}
                  expected: 1.0
            """.formatted(longExpr);

        assertThatThrownBy(() -> FormulaLoader.validate(yaml))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining(String.valueOf(FormulaConstants.MAX_EXPRESSION_LENGTH));
    }
}
