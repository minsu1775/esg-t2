package ai.claudecode.esgt2.entity.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserJpaEntity, UUID> {

    @Query("SELECT u FROM UserJpaEntity u WHERE u.tenantId = :tenantId AND u.email = :email AND u.isActive = true")
    Optional<UserJpaEntity> findActiveByTenantIdAndEmail(@Param("tenantId") UUID tenantId,
                                                          @Param("email") String email);
}
