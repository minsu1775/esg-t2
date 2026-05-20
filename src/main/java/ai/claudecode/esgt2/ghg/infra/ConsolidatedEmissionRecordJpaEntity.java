package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consolidated_emission_records")
@Getter
@NoArgsConstructor
public class ConsolidatedEmissionRecordJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID rootEntityId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String ghgType;

    @Column(nullable = false)
    private String consolidationMethod;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal totalEmission;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public ConsolidatedEmissionRecordJpaEntity(UUID id, UUID tenantId, UUID rootEntityId,
                                                int reportingYear, String scope, String ghgType,
                                                String consolidationMethod, BigDecimal totalEmission) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.rootEntityId = rootEntityId;
        this.reportingYear = reportingYear;
        this.scope = scope;
        this.ghgType = ghgType;
        this.consolidationMethod = consolidationMethod;
        this.totalEmission = totalEmission;
        this.createdAt = Instant.now();
    }
}
