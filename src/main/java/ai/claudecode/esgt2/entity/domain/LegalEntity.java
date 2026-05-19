package ai.claudecode.esgt2.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.util.UUID;

public record LegalEntity(
    UUID id,
    UUID tenantId,
    String name,
    String countryCode,
    LegalEntityType entityType,
    boolean isActive
) {
    public static LegalEntity create(CreateLegalEntityCommand cmd) {
        if (cmd.tenantId() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "tenantId는 필수입니다.");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "법인명은 필수입니다.");
        }
        if (cmd.countryCode() == null || cmd.countryCode().length() != 2
                || !cmd.countryCode().equals(cmd.countryCode().toUpperCase())) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "국가코드는 ISO 3166-1 alpha-2 대문자 2자리여야 합니다.");
        }
        if (cmd.entityType() == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "법인 유형은 필수입니다.");
        }

        return new LegalEntity(
            UUID.randomUUID(),
            cmd.tenantId(),
            cmd.name().strip(),
            cmd.countryCode(),
            cmd.entityType(),
            true
        );
    }
}
