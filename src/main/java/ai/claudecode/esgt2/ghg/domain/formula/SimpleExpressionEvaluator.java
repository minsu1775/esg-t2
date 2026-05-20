package ai.claudecode.esgt2.ghg.domain.formula;

import java.util.Map;

/**
 * test_cases 검증 전용 단순 수식 평가기.
 * 허용: 숫자 리터럴, 변수명, +, -, *, /, (, ).
 * 금지: T(...), new, SpEL, Reflection (07-formula-dsl.md).
 */
final class SimpleExpressionEvaluator {

    private final String expr;
    private final Map<String, Double> vars;
    private int pos;

    private SimpleExpressionEvaluator(String expr, Map<String, Double> vars) {
        this.expr = expr.replaceAll("\\s+", "");
        this.vars = vars;
        this.pos = 0;
    }

    static double evaluate(String expression, Map<String, Double> variables) {
        if (expression.length() > FormulaConstants.MAX_EXPRESSION_LENGTH) {
            throw new FormulaValidationException(
                "수식 길이 초과: " + FormulaConstants.MAX_EXPRESSION_LENGTH);
        }
        return new SimpleExpressionEvaluator(expression, variables).parseExpr(0);
    }

    private double parseExpr(int depth) {
        if (depth > FormulaConstants.MAX_EVAL_DEPTH) {
            throw new FormulaValidationException("평가 깊이 초과: MAX_EVAL_DEPTH=" + FormulaConstants.MAX_EVAL_DEPTH);
        }
        double result = parseTerm(depth + 1);
        while (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
            char op = expr.charAt(pos++);
            double right = parseTerm(depth + 1);
            result = op == '+' ? result + right : result - right;
        }
        return result;
    }

    private double parseTerm(int depth) {
        double result = parseFactor(depth + 1);
        while (pos < expr.length() && (expr.charAt(pos) == '*' || expr.charAt(pos) == '/')) {
            char op = expr.charAt(pos++);
            double right = parseFactor(depth + 1);
            result = op == '*' ? result * right : result / right;
        }
        return result;
    }

    private double parseFactor(int depth) {
        if (pos < expr.length() && expr.charAt(pos) == '(') {
            pos++; // skip '('
            double result = parseExpr(depth + 1);
            if (pos < expr.length() && expr.charAt(pos) == ')') pos++;
            return result;
        }
        if (pos < expr.length() && expr.charAt(pos) == '-') {
            pos++;
            return -parseFactor(depth + 1);
        }
        if (pos < expr.length() && Character.isLetter(expr.charAt(pos))) {
            return parseVariable();
        }
        return parseNumber();
    }

    private double parseVariable() {
        int start = pos;
        while (pos < expr.length() && (Character.isLetterOrDigit(expr.charAt(pos)) || expr.charAt(pos) == '_')) {
            pos++;
        }
        String name = expr.substring(start, pos);
        Double value = vars.get(name);
        if (value == null) throw new FormulaValidationException("알 수 없는 변수: " + name);
        return value;
    }

    private double parseNumber() {
        int start = pos;
        while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
            pos++;
        }
        String numStr = expr.substring(start, pos);
        if (numStr.length() > FormulaConstants.MAX_NUMBER_LENGTH) {
            throw new FormulaValidationException("숫자 리터럴 길이 초과");
        }
        if (numStr.isEmpty()) throw new FormulaValidationException("숫자 파싱 실패: pos=" + pos);
        return Double.parseDouble(numStr);
    }
}
