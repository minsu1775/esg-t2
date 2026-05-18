---
name: formula-dsl
description: Formula DSL 보안·DoS 방어, YAML 로더 멱등성, test_cases 게이트
paths:
  - "src/main/java/**/formula/**"
  - "src/main/java/**/re/**"
  - "src/main/resources/formulas/**"
  - "**/*Formula*.java"
  - "**/*Evaluator*.java"
  - "**/*Loader*.java"
  - "**/*.yaml"
---

# Formula DSL 규칙

## 평가기 아키텍처

커스텀 재귀 하강 파서만 사용: `Lexer → Parser → CustomExpressionEvaluator`

- 허용 연산자: `+ - * / ( )`
- 허용 함수: `abs min max sum if pow log`
- **절대 금지**: `T(...)`, `new`, SpEL 직접 사용, Reflection, `eval`-style 임의 코드 실행

```java
// ❌ 금지
ExpressionParser parser = new SpelExpressionParser();

// ✅ 필수
CustomExpressionEvaluator evaluator = new CustomExpressionEvaluator(FormulaConstants.MAX_EVAL_DEPTH);
```

## DoS 방어 한계값 (esg-t1 BUG-P5-03) — FormulaConstants 상수로 선언

```java
public final class FormulaConstants {
    public static final int MAX_EXPRESSION_LENGTH = 1000;   // 수식 전체 길이
    public static final int MAX_NUMBER_LENGTH     = 50;     // 숫자 리터럴 자릿수
    public static final int MAX_PARSER_DEPTH      = 50;     // Parser 재귀 깊이
    public static final int MAX_EVAL_DEPTH        = 50;     // Evaluator 재귀 깊이
}
```

한계값 초과 시 `FormulaValidationException`(400) 반환. 서버 스택 오버플로 없음.
상수를 코드에 직접 하드코딩 금지 — 반드시 `FormulaConstants.*` 참조.

## YAML 산식 포맷

```yaml
formula:
  code: EM-S1-FUEL
  version: "1.0"
  inputs:
    - var: fuel_consumption
      unit: GJ
    - var: ef
      lookup: emission_factor
      key: fuel_type
      gwp: AR6
  expression: "fuel_consumption * ef * 0.001"
  output_unit: tCO2e
  test_cases:                          # 비어 있으면 퍼블리시 거부
    - inputs: { fuel_consumption: 1000.0, ef: 56.3 }
      expected: 56.3
    - inputs: { fuel_consumption: 0.0, ef: 56.3 }
      expected: 0.0
```

## test_cases 게이트 — 필수

`test_cases` 비어 있으면 활성화 **거부**. 검증 없는 산식 배포 차단.

```java
if (formula.testCases().isEmpty()) {
    throw new FormulaValidationException("test_cases가 없으면 퍼블리시 불가");
}
// 모든 test_cases 통과 후에만 status = ACTIVE
```

## YAML 로더 멱등성 (esg-t1 BUG-P5-07)

**파일 레벨 skip 금지**. 항목 단위로 존재 여부 확인 후 없는 것만 INSERT.

```java
// ❌ 금지 — 파일 레벨 스킵
if (alreadyProcessed(filename)) return;

// ✅ 필수 — 항목 레벨 upsert
formulaRepository.upsert(formula);   // ON CONFLICT (code, version) DO UPDATE
```

`DataIntegrityViolationException` 발생 시 `WARN` 로그 + 계속 진행 (전체 중단 금지).

## 산식 버전 관리

- 활성 산식: `status = ACTIVE`
- 비활성화: `status = INACTIVE` (DELETE 없음)
- 산식 변경 시 새 버전 INSERT → 이전 버전 INACTIVE 처리
- `CalculationResult`에 사용된 `formula_version_id` 기록 → 재현성 보장

## Snapshot 재현성

과거 공시 재현 시 당시 활성 산식 버전으로 계산해야 함.
`VerificationSnapshot.formulaVersionId` 필드에 기록 필수.
