package ai.claudecode.esgt2.entity.infra;

import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "legal_entities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalEntityJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private LegalEntityType entityType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Builder
    public LegalEntityJpaEntity(UUID tenantId, String name, String countryCode,
                                 LegalEntityType entityType) {
        this.tenantId = tenantId;
        this.name = name;
        this.countryCode = countryCode;
        this.entityType = entityType;
        this.isActive = true;
        this.createdAt = OffsetDateTime.now();
    }
}
