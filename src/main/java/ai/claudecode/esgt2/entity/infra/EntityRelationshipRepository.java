package ai.claudecode.esgt2.entity.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EntityRelationshipRepository extends JpaRepository<EntityRelationshipJpaEntity, UUID> {

    @Query("SELECT r FROM EntityRelationshipJpaEntity r WHERE r.tenantId = :tenantId")
    List<EntityRelationshipJpaEntity> findByTenantId(@Param("tenantId") UUID tenantId);
}
