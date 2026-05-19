package ai.claudecode.esgt2.audit.infra;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuditLogJpaEntity> findFirstByTenantIdOrderByIdDesc(UUID tenantId);

    List<AuditLogJpaEntity> findByTenantIdOrderByIdAsc(UUID tenantId);
}
