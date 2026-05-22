package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 스냅샷 저장소 — append-only.
 * VerificationSnapshot은 불변이므로 JpaRepository 대신
 * {@link Repository} 마커 인터페이스 상속 → delete* 메서드 컴파일 타임 미노출 (08-persistence.md).
 */
public interface VerificationSnapshotRepository
        extends Repository<VerificationSnapshotJpaEntity, UUID> {

    VerificationSnapshotJpaEntity save(VerificationSnapshotJpaEntity entity);

    Optional<VerificationSnapshotJpaEntity> findById(UUID id);

    Optional<VerificationSnapshotJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
