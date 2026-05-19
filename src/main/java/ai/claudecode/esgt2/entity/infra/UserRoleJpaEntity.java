package ai.claudecode.esgt2.entity.infra;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "entity_id")
    private UUID entityId;

    @Builder
    public UserRoleJpaEntity(UUID userId, String role, UUID entityId) {
        this.userId = userId;
        this.role = role;
        this.entityId = entityId;
    }
}
