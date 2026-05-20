package ai.claudecode.esgt2.ghg.domain.formula;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Formula YAML 검증기.
 * test_cases 게이트: 빈 test_cases → 활성화 거부 (07-formula-dsl.md).
 * DoS 방어: MAX_EXPRESSION_LENGTH 초과 시 거부.
 */
public final class FormulaLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private FormulaLoader() {}

    /**
     * YAML 문자열을 파싱하고 test_cases 게이트를 통과하면 true 반환.
     * 실패 시 FormulaValidationException throw.
     */
    public static boolean validate(String yaml) {
        try {
            var root = YAML_MAPPER.readTree(yaml);
            var formula = root.get("formula");

            String expression = formula.get("expression").asText();
            if (expression.length() > FormulaConstants.MAX_EXPRESSION_LENGTH) {
                throw new FormulaValidationException(
                    "수식 길이가 MAX_EXPRESSION_LENGTH(%d)를 초과합니다: %d자"
                        .formatted(FormulaConstants.MAX_EXPRESSION_LENGTH, expression.length()));
            }

            var testCasesNode = formula.get("test_cases");
            if (testCasesNode == null || !testCasesNode.isArray() || testCasesNode.isEmpty()) {
                throw new FormulaValidationException(
                    "test_cases가 비어 있으면 퍼블리시 불가 (07-formula-dsl.md)");
            }

            // 각 test_case 실행 검증
            for (var tc : testCasesNode) {
                Map<String, Double> inputs = YAML_MAPPER.convertValue(tc.get("inputs"),
                    YAML_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Double.class));
                double expected = tc.get("expected").asDouble();
                double actual = SimpleExpressionEvaluator.evaluate(expression, inputs);
                if (Math.abs(actual - expected) > 1e-6) {
                    throw new FormulaValidationException(
                        "test_cases 불일치: expected=%.6f, actual=%.6f".formatted(expected, actual));
                }
            }

            return true;
        } catch (FormulaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new FormulaValidationException("YAML 파싱 오류: " + e.getMessage());
        }
    }
}
