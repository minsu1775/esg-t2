package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emission_records")
@Getter
@NoArgsConstructor
public class EmissionRecordJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    private UUID activityDataId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false)
    private String scope;

    private Integer scope3Category;   // Scope 3 카테고리 번호 (1~16), Scope 1/2는 null

    @Column(nullable = false)
    private String ghgType;

    @Column(nullable = false)
    private UUID emissionFactorId;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal rawEmission;

    @Column(nullable = false)
    private boolean isConsolidated;

    @Column(nullable = false)
    private Instant calculatedAt;

    @Builder
    public EmissionRecordJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                    UUID activityDataId, int reportingYear,
                                    String scope, Integer scope3Category,
                                    String ghgType,
                                    UUID emissionFactorId, BigDecimal rawEmission) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.activityDataId = activityDataId;
        this.reportingYear = reportingYear;
        this.scope = scope;
        this.scope3Category = scope3Category;
        this.ghgType = ghgType != null ? ghgType : "CO2E";
        this.emissionFactorId = emissionFactorId;
        this.rawEmission = rawEmission;
        this.isConsolidated = false;
        this.calculatedAt = Instant.now();
    }
}
