package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scope3_coverage_reports")
@Getter
@NoArgsConstructor
public class Scope3CoverageReportJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String includedCategories;    // JSON: "[1,2,11]"

    @Column(columnDefinition = "TEXT")
    private String excludedCategories;    // JSON: "[4,6]" or null

    @Column(columnDefinition = "TEXT")
    private String exclusionReasons;      // JSON: {"4":"사유"} or null

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePct;

    @Column(nullable = false)
    private boolean meets95PctThreshold;

    @Column(nullable = false)
    private Instant generatedAt;

    @Builder
    public Scope3CoverageReportJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                          int reportingYear,
                                          String includedCategories,
                                          String excludedCategories,
                                          String exclusionReasons,
                                          BigDecimal coveragePct,
                                          boolean meets95PctThreshold) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.includedCategories = includedCategories;
        this.excludedCategories = excludedCategories;
        this.exclusionReasons = exclusionReasons;
        this.coveragePct = coveragePct;
        this.meets95PctThreshold = meets95PctThreshold;
        this.generatedAt = Instant.now();
    }
}
