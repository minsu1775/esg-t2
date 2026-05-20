package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.entity.api.EntityRelationship;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityRelationshipTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PARENT_ID = UUID.randomUUID();
    private static final UUID CHILD_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);

    @Test
    void 유효한_입력으로_관계가_생성된다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            new BigDecimal("0.6000"), ConsolidationMethod.EQUITY, FROM, null);

        EntityRelationship rel = EntityRelationship.create(cmd);

        assertThat(rel.tenantId()).isEqualTo(TENANT_ID);
        assertThat(rel.parentId()).isEqualTo(PARENT_ID);
        assertThat(rel.childId()).isEqualTo(CHILD_ID);
        assertThat(rel.ownershipRatio()).isEqualByComparingTo(new BigDecimal("0.6000"));
        assertThat(rel.method()).isEqualTo(ConsolidationMethod.EQUITY);
    }

    @Test
    void 지분율이_0이면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            BigDecimal.ZERO, ConsolidationMethod.EQUITY, FROM, null);

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 지분율이_음수이면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            new BigDecimal("-0.1"), ConsolidationMethod.EQUITY, FROM, null);

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 지분율이_1초과이면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            new BigDecimal("1.0001"), ConsolidationMethod.EQUITY, FROM, null);

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 지분율_1_0은_유효하다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            BigDecimal.ONE, ConsolidationMethod.OPERATIONAL_CONTROL, FROM, null);

        EntityRelationship rel = EntityRelationship.create(cmd);
        assertThat(rel.ownershipRatio()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void 부모와_자식이_동일하면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, PARENT_ID,
            new BigDecimal("0.5"), ConsolidationMethod.EQUITY, FROM, null);

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void effectiveFrom이_null이면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            new BigDecimal("0.5"), ConsolidationMethod.EQUITY, null, null);

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }

    @Test
    void effectiveTo가_effectiveFrom보다_이전이면_예외가_발생한다() {
        var cmd = new CreateEntityRelationshipCommand(
            TENANT_ID, PARENT_ID, CHILD_ID,
            new BigDecimal("0.5"), ConsolidationMethod.EQUITY,
            FROM, FROM.minusDays(1));

        assertThatThrownBy(() -> EntityRelationship.create(cmd))
            .isInstanceOf(EsgException.class)
            .satisfies(e -> assertThat(((EsgException) e).getErrorCode())
                .isEqualTo(EsgErrorCode.VALIDATION_FAILED));
    }
}
