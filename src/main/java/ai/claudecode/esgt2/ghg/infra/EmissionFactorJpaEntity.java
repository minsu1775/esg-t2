package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "emission_factors")
@Getter
@NoArgsConstructor
public class EmissionFactorJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String category;

    private String subCategory;

    private String countryCode;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false)
    private String gwpSource;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal factorValue;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public EmissionFactorJpaEntity(UUID id, String source, String category,
                                    String subCategory, String countryCode,
                                    int reportingYear, String gwpSource,
                                    BigDecimal factorValue, String unit,
                                    LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id != null ? id : UUID.randomUUID();
        this.source = source;
        this.category = category;
        this.subCategory = subCategory;
        this.countryCode = countryCode;
        this.reportingYear = reportingYear;
        this.gwpSource = gwpSource;
        this.factorValue = factorValue;
        this.unit = unit;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public void deactivate(LocalDate endDate) {
        this.isActive = false;
        this.effectiveTo = endDate;
    }
}
