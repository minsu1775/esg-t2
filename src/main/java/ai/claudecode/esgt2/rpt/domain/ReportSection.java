package ai.claudecode.esgt2.rpt.domain;

import java.math.BigDecimal;

/**
 * 보고서 섹션 단위 — KSSB2 공시 항목 하나.
 * yoyDelta: 전년 대비 증감률(%). 전년 데이터 없으면 null.
 */
public record ReportSection(
    String itemCode,        // e.g. "KSSB2.S1"
    String title,           // e.g. "Scope 1 직접 배출량"
    BigDecimal value,       // 현재 연도 배출량 (tCO2e)
    BigDecimal yoyDelta     // 전년 대비 증감률 (%) — null 허용
) {}
