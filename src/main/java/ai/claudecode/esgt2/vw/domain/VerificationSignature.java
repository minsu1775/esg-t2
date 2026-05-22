package ai.claudecode.esgt2.vw.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 검증 완료 서명 도메인 객체.
 * 스냅샷당 1건만 허용 (verification_signatures.snapshot_id UNIQUE).
 * 서명 후 스냅샷 본체(verification_snapshots)는 변경되지 않아 불변성 유지.
 */
public record VerificationSignature(
    UUID id,
    UUID snapshotId,
    UUID tenantId,
    UUID signedBy,
    Instant signedAt,
    String signNote
) {
    public static VerificationSignature create(UUID snapshotId, UUID tenantId,
                                                UUID signedBy, String signNote) {
        return new VerificationSignature(
            UUID.randomUUID(), snapshotId, tenantId, signedBy, Instant.now(), signNote
        );
    }
}
