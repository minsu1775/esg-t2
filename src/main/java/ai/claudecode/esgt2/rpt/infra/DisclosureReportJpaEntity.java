package ai.claudecode.esgt2.rpt.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "disclosure_reports")
@Getter
@NoArgsConstructor
public class DisclosureReportJpaEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false, length = 30)
    private String framework;

    @Column(nullable = false, length = 20)
    private String status;

    /**
     * content JSONB: { sections: [...], emissionsByScope: {...} }
     * Hibernate의 @JdbcTypeCode(SqlTypes.JSON)으로 JSONB 직접 매핑.
     */
    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String content;

    @Column(nullable = false)
    private Instant generatedAt;

    private Instant submittedAt;
    private Instant approvedAt;
    private UUID approvedBy;
    private String rejectionReason;

    @Builder
    public DisclosureReportJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                      int reportingYear, String framework, String status,
                                      String content, Instant generatedAt,
                                      Instant submittedAt, Instant approvedAt,
                                      UUID approvedBy, String rejectionReason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.framework = framework;
        this.status = status != null ? status : "DRAFT";
        this.content = content != null ? content : "{}";
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.submittedAt = submittedAt;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.rejectionReason = rejectionReason;
    }

    /** 상태 업데이트 — 도메인 승인 결과 반영 전용 */
    public void updateFromDomain(String status, Instant submittedAt,
                                  Instant approvedAt, UUID approvedBy, String rejectionReason) {
        this.status = status;
        this.submittedAt = submittedAt;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.rejectionReason = rejectionReason;
    }

    public Map<String, BigDecimal> emissionsByScopeFromContent() {
        try {
            var tree = MAPPER.readTree(content);
            var node = tree.get("emissionsByScope");
            if (node == null) return Map.of();
            return MAPPER.convertValue(node, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
