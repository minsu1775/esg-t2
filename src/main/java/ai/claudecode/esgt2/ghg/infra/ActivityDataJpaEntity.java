package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity_data")
@Getter
@NoArgsConstructor
public class ActivityDataJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false)
    private String category;

    private String subCategory;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(precision = 20, scale = 6)
    private BigDecimal standardValue;

    private String standardUnit;

    @Column(nullable = false)
    private String dataSource;

    @Column(nullable = false)
    private String dataQuality;

    @Column(nullable = false)
    private String status;

    private UUID submittedBy;
    private UUID approvedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    public ActivityDataJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                  int reportingYear, String category, String subCategory,
                                  BigDecimal quantity, String unit, String countryCode,
                                  BigDecimal standardValue, String standardUnit,
                                  String dataSource, String dataQuality) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.category = category;
        this.subCategory = subCategory;
        this.quantity = quantity;
        this.unit = unit;
        this.countryCode = countryCode;
        this.standardValue = standardValue;
        this.standardUnit = standardUnit;
        this.dataSource = dataSource != null ? dataSource : "MANUAL";
        this.dataQuality = dataQuality != null ? dataQuality : "AVERAGE_DATA";
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
