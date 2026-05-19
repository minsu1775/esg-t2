package ai.claudecode.esgt2.entity.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    @Query("SELECT r FROM UserRoleJpaEntity r WHERE r.userId = :userId")
    List<UserRoleJpaEntity> findByUserId(@Param("userId") UUID userId);
}
