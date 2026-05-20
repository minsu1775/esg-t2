package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityRelationship;
import ai.claudecode.esgt2.entity.api.EntityRelationshipGraph;
import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ConsolidationEngineTest {

    static final UUID PARENT = UUID.randomUUID();
    static final UUID CHILD1 = UUID.randomUUID();
    static final UUID CHILD2 = UUID.randomUUID();
    static final UUID GRANDCHILD = UUID.randomUUID();

    @Test
    void 단일_법인_직접_배출량만_있을_때_그대로_반환(/* T-4-02 */) {
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of());
        Map<UUID, BigDecimal> emissions = Map.of(PARENT, new BigDecimal("100.000000"));

        ConsolidationResult result = ConsolidationEngine.consolidateEquity(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    @Test
    void Equity_Method_3법인_지분율_계산_정확도(/* T-4-02 */) {
        // PARENT 100%, CHILD1 60%, CHILD2 40% 소유
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.60", ConsolidationMethod.EQUITY),
            rel(PARENT, CHILD2, "0.40", ConsolidationMethod.EQUITY)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("200.000000"),
            CHILD2, new BigDecimal("300.000000")
        );

        // 100 + 0.60×200 + 0.40×300 = 100 + 120 + 120 = 340
        ConsolidationResult result = ConsolidationEngine.consolidateEquity(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("340.000000"));
    }

    @Test
    void 이중_계상_제거_A_B_C_체인(/* T-4-04 */) {
        // A → B (60%) → C (70%)
        // 올바른 결과: E_A + 0.60×E_B + (0.60×0.70)×E_C = 100 + 60×1 + 0.42×50 = 100+60+21 = 181
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.60", ConsolidationMethod.EQUITY),
            rel(CHILD1, GRANDCHILD, "0.70", ConsolidationMethod.EQUITY)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("100.000000"),  // E_B=100, 60%→60
            GRANDCHILD, new BigDecimal("50.000000")  // E_C=50, 42%→21
        );

        ConsolidationResult result = ConsolidationEngine.consolidateEquity(
            PARENT, emissions, graph);

        // 100 + 0.60×100 + 0.42×50 = 100 + 60 + 21 = 181
        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("181.000000"));
    }

    @Test
    void 순환_지분_구조_탐지_시_예외(/* T-4-03 */) {
        // A→B→C→A 순환 참조
        assertThatThrownBy(() -> EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.60", ConsolidationMethod.EQUITY),
            rel(CHILD1, GRANDCHILD, "0.70", ConsolidationMethod.EQUITY),
            rel(GRANDCHILD, PARENT, "0.30", ConsolidationMethod.EQUITY)
        ))).isInstanceOf(EsgException.class)
          .hasMessageContaining("순환 참조");
    }

    @Test
    void Operational_Control_직접_지배_법인만_포함(/* T-4-02 */) {
        // PARENT: CHILD1 80%(지배), CHILD2 30%(비지배)
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.80", ConsolidationMethod.OPERATIONAL_CONTROL),
            rel(PARENT, CHILD2, "0.30", ConsolidationMethod.OPERATIONAL_CONTROL)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("200.000000"),
            CHILD2, new BigDecimal("300.000000")
        );

        // CHILD1(80% > 50%) 포함, CHILD2(30% ≤ 50%) 제외 → 100 + 200 = 300
        ConsolidationResult result = ConsolidationEngine.consolidateOperationalControl(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("300.000000"));
    }

    @Test
    void Operational_Control_3단계_체인_모든_링크_지배_시_하위도_포함() {
        // PARENT→CHILD1(80%)→GRANDCHILD(70%): 모든 링크 > 50% → GRANDCHILD 포함
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.80", ConsolidationMethod.OPERATIONAL_CONTROL),
            rel(CHILD1, GRANDCHILD, "0.70", ConsolidationMethod.OPERATIONAL_CONTROL)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("200.000000"),
            GRANDCHILD, new BigDecimal("150.000000")
        );

        // PARENT+CHILD1+GRANDCHILD 전부 포함 → 100 + 200 + 150 = 450
        ConsolidationResult result = ConsolidationEngine.consolidateOperationalControl(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("450.000000"));
        assertThat(result.entityContributions()).containsKey(GRANDCHILD);
    }

    @Test
    void Operational_Control_3단계_중간_링크_비지배면_하위_전체_제외() {
        // PARENT→CHILD1(40%)→GRANDCHILD(70%): CHILD1 링크가 40% ≤ 50% → CHILD1, GRANDCHILD 모두 제외
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.40", ConsolidationMethod.OPERATIONAL_CONTROL),
            rel(CHILD1, GRANDCHILD, "0.70", ConsolidationMethod.OPERATIONAL_CONTROL)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("200.000000"),
            GRANDCHILD, new BigDecimal("150.000000")
        );

        // PARENT만 포함 → 100
        ConsolidationResult result = ConsolidationEngine.consolidateOperationalControl(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("100.000000"));
        assertThat(result.entityContributions()).doesNotContainKey(CHILD1);
        assertThat(result.entityContributions()).doesNotContainKey(GRANDCHILD);
    }

    @Test
    void 지분율_수정_후_재계산_일관성(/* T-4-05 */) {
        // PARENT → CHILD1 50% (경계 케이스)
        EntityRelationshipGraph graph50 = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.50", ConsolidationMethod.EQUITY)
        ));
        // PARENT → CHILD1 60%
        EntityRelationshipGraph graph60 = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.60", ConsolidationMethod.EQUITY)
        ));
        Map<UUID, BigDecimal> emissions = Map.of(
            PARENT, new BigDecimal("100.000000"),
            CHILD1, new BigDecimal("200.000000")
        );

        BigDecimal result50 = ConsolidationEngine.consolidateEquity(PARENT, emissions, graph50)
            .totalConsolidatedEmission();
        BigDecimal result60 = ConsolidationEngine.consolidateEquity(PARENT, emissions, graph60)
            .totalConsolidatedEmission();

        // 50%: 100 + 100 = 200, 60%: 100 + 120 = 220
        assertThat(result50).isEqualByComparingTo(new BigDecimal("200.000000"));
        assertThat(result60).isEqualByComparingTo(new BigDecimal("220.000000"));
        assertThat(result60).isGreaterThan(result50);
    }

    @Test
    void 배출량_없는_자회사는_0으로_처리() {
        EntityRelationshipGraph graph = EntityRelationshipGraph.of(List.of(
            rel(PARENT, CHILD1, "0.80", ConsolidationMethod.EQUITY)
        ));
        // CHILD1 배출량 없음
        Map<UUID, BigDecimal> emissions = Map.of(PARENT, new BigDecimal("100.000000"));

        ConsolidationResult result = ConsolidationEngine.consolidateEquity(
            PARENT, emissions, graph);

        assertThat(result.totalConsolidatedEmission())
            .isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    // ── 헬퍼 메서드 ────────────────────────────────────────────────

    private static EntityRelationship rel(UUID parent, UUID child, String ratio, ConsolidationMethod method) {
        return new EntityRelationship(UUID.randomUUID(), UUID.randomUUID(),
            parent, child, new BigDecimal(ratio), method,
            LocalDate.of(2025, 1, 1), null);
    }
}
