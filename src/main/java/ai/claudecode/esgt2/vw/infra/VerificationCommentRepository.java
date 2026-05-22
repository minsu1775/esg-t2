package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 검증 코멘트 저장소 — append-only.
 */
public interface VerificationCommentRepository
        extends Repository<VerificationCommentJpaEntity, UUID> {

    VerificationCommentJpaEntity save(VerificationCommentJpaEntity entity);

    List<VerificationCommentJpaEntity> findBySnapshotIdAndTenantIdOrderByCreatedAtAsc(
        UUID snapshotId, UUID tenantId);
}
