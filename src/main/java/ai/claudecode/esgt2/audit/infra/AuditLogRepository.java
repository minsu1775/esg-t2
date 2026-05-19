package ai.claudecode.esgt2.audit.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Append-only: delete 메서드 컴파일 타임 미노출 (08-persistence.md)
public interface AuditLogRepository extends Repository<AuditLogJpaEntity, Long> {

    AuditLogJpaEntity save(AuditLogJpaEntity entity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditLogJpaEntity> findFirstByTenantIdOrderByIdDesc(UUID tenantId);

    List<AuditLogJpaEntity> findByTenantIdOrderByIdAsc(UUID tenantId);

    @Query("SELECT DISTINCT a.tenantId FROM AuditLogJpaEntity a")
    List<UUID> findDistinctTenantIds();
}
