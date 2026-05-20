package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Scope3CoverageReport(
    UUID id,
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    List<Integer> includedCategories,      // 실제 배출량이 있는 카테고리 번호
    List<Integer> excludedCategories,      // 추정치만 있는 카테고리 번호
    Map<Integer, String> exclusionReasons, // 제외 사유 (카테고리번호 → 사유)
    BigDecimal coveragePct,                // 배출량 기반 커버리지 비율 (0~100)
    boolean meets95PctThreshold            // 95% 임계값 충족 여부
) {}
