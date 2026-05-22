package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 검증 서명 저장소 — append-only.
 * 서명은 스냅샷당 1건만 허용 (DB 레벨 UNIQUE 제약).
 */
public interface VerificationSignatureRepository
        extends Repository<VerificationSignatureJpaEntity, UUID> {

    VerificationSignatureJpaEntity save(VerificationSignatureJpaEntity entity);

    Optional<VerificationSignatureJpaEntity> findBySnapshotIdAndTenantId(
        UUID snapshotId, UUID tenantId);

    boolean existsBySnapshotIdAndTenantId(UUID snapshotId, UUID tenantId);
}
