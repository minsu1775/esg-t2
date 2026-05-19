package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EntityRelationship(
    UUID id,
    UUID tenantId,
    UUID parentId,
    UUID childId,
    BigDecimal ownershipRatio,
    ConsolidationMethod method,
    LocalDate effectiveFrom,
    LocalDate effectiveTo
) {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public static EntityRelationship create(CreateEntityRelationshipCommand cmd) {
        if (cmd.tenantId() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "tenantId는 필수입니다.");
        }
        if (cmd.parentId() == null || cmd.childId() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "parentId와 childId는 필수입니다.");
        }
        if (cmd.parentId().equals(cmd.childId())) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "부모 법인과 자식 법인은 동일할 수 없습니다.");
        }
        if (cmd.ownershipRatio() == null
                || cmd.ownershipRatio().compareTo(ZERO) <= 0
                || cmd.ownershipRatio().compareTo(ONE) > 0) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "지분율은 0 초과 1 이하여야 합니다.");
        }
        if (cmd.method() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "연결 방법은 필수입니다.");
        }
        if (cmd.effectiveFrom() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "유효 시작일은 필수입니다.");
        }
        if (cmd.effectiveTo() != null && !cmd.effectiveTo().isAfter(cmd.effectiveFrom())) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "유효 종료일은 시작일 이후여야 합니다.");
        }

        return new EntityRelationship(
            UUID.randomUUID(),
            cmd.tenantId(),
            cmd.parentId(),
            cmd.childId(),
            cmd.ownershipRatio(),
            cmd.method(),
            cmd.effectiveFrom(),
            cmd.effectiveTo()
        );
    }
}
