package ai.claudecode.esgt2.entity.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LegalEntityRepository extends JpaRepository<LegalEntityJpaEntity, UUID> {

    @Query("SELECT e FROM LegalEntityJpaEntity e WHERE e.tenantId = :tenantId AND e.isActive = true")
    List<LegalEntityJpaEntity> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}
