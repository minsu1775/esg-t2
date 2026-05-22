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

    private Integer lifetimeYears;   // Cat.11 전용 (nullable)

    private UUID correctionOf;       // 정정 원본 ID (null = 최초 등록)
    private String correctionReason; // 정정 사유 (null = 최초 등록)

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
                                  String dataSource, String dataQuality,
                                  Integer lifetimeYears,
                                  UUID correctionOf, String correctionReason) {
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
        this.lifetimeYears = lifetimeYears;
        this.correctionOf = correctionOf;
        this.correctionReason = correctionReason;
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // 상태 전이 메서드 — setStatus() 직접 호출 금지 (01-domain-architecture.md)

    /** ARCHIVED 상태 전이 — 정정된 원본 레코드 표식 (P1: INSERT-only 정정 패턴) */
    public void archive() {
        if ("ARCHIVED".equals(this.status)) throw new IllegalStateException("이미 보관된 데이터입니다");
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }

    public void submit(UUID actorId) {
        if (!"DRAFT".equals(this.status)) throw new IllegalStateException("DRAFT 상태만 제출 가능");
        this.status = "PENDING";
        this.submittedBy = actorId;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID actorId) {
        if (!"PENDING".equals(this.status)) throw new IllegalStateException("PENDING 상태만 승인 가능");
        this.status = "APPROVED";
        this.approvedBy = actorId;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("반려 사유는 필수입니다");
        if (!"PENDING".equals(this.status)) throw new IllegalStateException("PENDING 상태만 반려 가능");
        this.status = "REJECTED";
        this.updatedAt = Instant.now();
    }
}
