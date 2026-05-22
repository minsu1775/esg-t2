package ai.claudecode.esgt2.supply.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierInvitationRepository extends JpaRepository<SupplierInvitationJpaEntity, UUID> {

    Optional<SupplierInvitationJpaEntity> findByToken(UUID token);

    boolean existsByTenantIdAndEntityIdAndEmailAndStatus(
        UUID tenantId, UUID entityId, String email, String status);
}
