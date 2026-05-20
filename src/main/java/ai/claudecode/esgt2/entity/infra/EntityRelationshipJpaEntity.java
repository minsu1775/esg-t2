package ai.claudecode.esgt2.entity.infra;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "entity_relationships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntityRelationshipJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "child_id", nullable = false)
    private UUID childId;

    @Column(name = "ownership_ratio", nullable = false, precision = 5, scale = 4)
    private BigDecimal ownershipRatio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ConsolidationMethod method;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public EntityRelationshipJpaEntity(UUID tenantId, UUID parentId, UUID childId,
                                        BigDecimal ownershipRatio, ConsolidationMethod method,
                                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.tenantId = tenantId;
        this.parentId = parentId;
        this.childId = childId;
        this.ownershipRatio = ownershipRatio;
        this.method = method;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.createdAt = OffsetDateTime.now();
    }
}
