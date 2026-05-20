package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityRelationship;
import ai.claudecode.esgt2.entity.api.EntityRelationshipGraph;
import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityRelationshipGraphTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);

    private EntityRelationship rel(UUID parent, UUID child, String ratio) {
        return EntityRelationship.create(new CreateEntityRelationshipCommand(
            TENANT_ID, parent, child, new BigDecimal(ratio),
            ConsolidationMethod.EQUITY, FROM, null));
    }

    @Test
    void 직계_자회사를_조회한다() {
        // A -60%-> B, A -40%-> C
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60"), rel(A, C, "0.40")));

        assertThat(graph.directChildren(A)).containsExactlyInAnyOrder(B, C);
    }

    @Test
    void 자식이_없는_법인의_직계_자회사는_빈_목록이다() {
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60")));

        assertThat(graph.directChildren(B)).isEmpty();
    }

    @Test
    void 모든_하위_법인을_재귀_탐색한다() {
        // A -60%-> B -50%-> C
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60"), rel(B, C, "0.50")));

        assertThat(graph.allDescendants(A)).containsExactlyInAnyOrder(B, C);
    }

    @Test
    void 누적_지분율을_계산한다() {
        // A -60%-> B -50%-> C  =>  A의 C 실질 소유율 = 0.30
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60"), rel(B, C, "0.50")));

        BigDecimal effectiveRatio = graph.effectiveOwnershipRatio(A, C);
        assertThat(effectiveRatio).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    void 관계가_없는_법인쌍의_실질_소유율은_0이다() {
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60")));

        assertThat(graph.effectiveOwnershipRatio(A, D)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 사이클이_존재하면_예외가_발생한다() {
        // A -> B -> C -> A  (사이클)
        assertThatThrownBy(() ->
            EntityRelationshipGraph.of(List.of(
                rel(A, B, "0.60"),
                rel(B, C, "0.50"),
                rel(C, A, "0.30")  // 사이클
            ))
        ).isInstanceOf(EsgException.class);
    }

    @Test
    void 루트_법인_목록을_반환한다() {
        // A -> B -> C; D (독립)
        var graph = EntityRelationshipGraph.of(List.of(rel(A, B, "0.60"), rel(B, C, "0.50")));

        // A와 D는 부모가 없는 루트
        assertThat(graph.roots()).contains(A);
        assertThat(graph.roots()).doesNotContain(B, C);
    }
}
