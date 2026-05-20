package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * 연결 집계 결과: 루트 법인 기준 전체 연결 배출량 + 법인별 기여분
 */
public record ConsolidationResult(
    UUID rootEntityId,
    BigDecimal totalConsolidatedEmission,
    ConsolidationMethod method,
    Map<UUID, BigDecimal> entityContributions   // entityId → 가중 배출량 (법인별 뷰용)
) {}
