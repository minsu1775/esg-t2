package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consolidated_emission_contributions")
@Getter
@NoArgsConstructor
public class ConsolidatedEmissionContributionJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID consolidatedRecordId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(precision = 5, scale = 4)
    private BigDecimal ownershipRatio;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal weightedEmission;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public ConsolidatedEmissionContributionJpaEntity(UUID id, UUID consolidatedRecordId,
                                                      UUID entityId, BigDecimal ownershipRatio,
                                                      BigDecimal weightedEmission) {
        this.id = id != null ? id : UUID.randomUUID();
        this.consolidatedRecordId = consolidatedRecordId;
        this.entityId = entityId;
        this.ownershipRatio = ownershipRatio;
        this.weightedEmission = weightedEmission;
        this.createdAt = Instant.now();
    }
}
