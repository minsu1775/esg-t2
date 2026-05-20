package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityRelationshipGraph;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 다법인 연결 집계 도메인 서비스.
 * GHG Protocol 기준 Equity Method / Operational Control Method 구현.
 */
public final class ConsolidationEngine {

    private static final BigDecimal CONTROL_THRESHOLD = new BigDecimal("0.50");

    private ConsolidationEngine() {}

    /**
     * Equity Method 연결 집계.
     * ConsolidatedEmission = Σ (entityDirectEmission × effectiveOwnershipRatio)
     * 이중 계상 제거: 각 법인의 직접 배출량에 루트로부터의 실질 소유율을 곱하여 합산하므로
     * A→B→C 체인에서 C의 배출량은 정확히 한 번(ratio = ownerAB × ownerBC)만 계상됨.
     */
    public static ConsolidationResult consolidateEquity(
            UUID rootEntityId,
            Map<UUID, BigDecimal> entityDirectEmissions,
            EntityRelationshipGraph graph) {

        Map<UUID, BigDecimal> contributions = new HashMap<>();

        BigDecimal rootEmission = entityDirectEmissions.getOrDefault(rootEntityId, BigDecimal.ZERO)
            .setScale(6, RoundingMode.HALF_UP);
        contributions.put(rootEntityId, rootEmission);
        BigDecimal total = rootEmission;

        for (UUID descendant : graph.allDescendants(rootEntityId)) {
            BigDecimal directEmission = entityDirectEmissions.getOrDefault(descendant, BigDecimal.ZERO);
            BigDecimal ratio = graph.effectiveOwnershipRatio(rootEntityId, descendant);
            BigDecimal weighted = directEmission.multiply(ratio).setScale(6, RoundingMode.HALF_UP);
            contributions.put(descendant, weighted);
            total = total.add(weighted);
        }

        return new ConsolidationResult(rootEntityId, total.setScale(6, RoundingMode.HALF_UP),
            ConsolidationMethod.EQUITY, Map.copyOf(contributions));
    }

    /**
     * Operational Control Method 연결 집계 (GHG Protocol 준수).
     * 루트에서 하위 법인까지의 직접 지배 체인 상 모든 링크의 지분율이 50% 초과인 경우
     * 해당 법인의 배출량을 100% 포함한다.
     * — effectiveOwnershipRatio(경로 곱) 임계값 방식과 다름 —
     * 예: A→B(60%)→C(70%): 모든 링크 > 50% → C 포함.
     *     A→B(40%)→C(70%): B 링크 ≤ 50% → B, C 모두 제외.
     */
    public static ConsolidationResult consolidateOperationalControl(
            UUID rootEntityId,
            Map<UUID, BigDecimal> entityDirectEmissions,
            EntityRelationshipGraph graph) {

        Map<UUID, BigDecimal> contributions = new HashMap<>();

        BigDecimal rootEmission = entityDirectEmissions.getOrDefault(rootEntityId, BigDecimal.ZERO)
            .setScale(6, RoundingMode.HALF_UP);
        contributions.put(rootEntityId, rootEmission);
        BigDecimal total = rootEmission;

        for (UUID descendant : graph.allDescendants(rootEntityId)) {
            if (graph.hasDirectControlChain(rootEntityId, descendant, CONTROL_THRESHOLD)) {
                BigDecimal directEmission = entityDirectEmissions.getOrDefault(descendant, BigDecimal.ZERO)
                    .setScale(6, RoundingMode.HALF_UP);
                contributions.put(descendant, directEmission);
                total = total.add(directEmission);
            }
        }

        return new ConsolidationResult(rootEntityId, total.setScale(6, RoundingMode.HALF_UP),
            ConsolidationMethod.OPERATIONAL_CONTROL, Map.copyOf(contributions));
    }
}
