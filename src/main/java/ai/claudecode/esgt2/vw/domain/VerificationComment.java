package ai.claudecode.esgt2.vw.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.time.Instant;
import java.util.UUID;

/**
 * 검증 코멘트 도메인 객체.
 */
public record VerificationComment(
    UUID id,
    UUID snapshotId,
    UUID tenantId,
    UUID authorId,
    String body,
    Instant createdAt
) {
    public static VerificationComment create(UUID snapshotId, UUID tenantId,
                                              UUID authorId, String body) {
        if (body == null || body.isBlank()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "코멘트 내용은 필수입니다.");
        }
        return new VerificationComment(
            UUID.randomUUID(), snapshotId, tenantId, authorId, body, Instant.now()
        );
    }
}
