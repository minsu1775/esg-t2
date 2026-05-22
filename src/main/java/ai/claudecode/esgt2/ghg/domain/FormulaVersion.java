package ai.claudecode.esgt2.ghg.domain;

import java.util.UUID;

/**
 * Formula 버전 도메인 객체 — 산식 코드·버전·수식·상태를 표현.
 * status: ACTIVE(활성) / INACTIVE(비활성). DELETE 없음 (P1 재현성).
 * test_cases 게이트를 통과한 산식만 ACTIVE로 등록된다 (T-6B-07).
 */
public record FormulaVersion(
    UUID id,
    String code,
    String version,
    String expression,
    String ghgCategory,   // 적용 GHG 카테고리 (nullable)
    String yamlContent,
    String status
) {
    /** 최초 등록 팩토리 — status는 항상 ACTIVE로 시작 */
    public static FormulaVersion create(String code, String version, String expression,
                                        String ghgCategory, String yamlContent) {
        return new FormulaVersion(
            UUID.randomUUID(), code, version, expression, ghgCategory, yamlContent, "ACTIVE");
    }
}
