package ai.claudecode.esgt2.rpt.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 공시 보고서 도메인 객체.
 * 상태 전이: DRAFT → SUBMITTED → APPROVED | REJECTED (T-7-11).
 * setStatus() 직접 호출 없음 — 명시적 메서드(submit/approve/reject)만 허용.
 */
public class DisclosureReport {

    private final UUID id;
    private final UUID tenantId;
    private final UUID entityId;
    private final int reportingYear;
    private final String framework;
    private final List<ReportSection> sections;
    private final Map<String, BigDecimal> emissionsByScope;
    private String status;          // DRAFT / SUBMITTED / APPROVED / REJECTED
    private Instant submittedAt;
    private Instant approvedAt;
    private UUID approvedBy;
    private String rejectionReason;
    private final Instant generatedAt;

    private DisclosureReport(UUID id, UUID tenantId, UUID entityId,
                              int reportingYear, String framework,
                              List<ReportSection> sections,
                              Map<String, BigDecimal> emissionsByScope) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.framework = framework;
        this.sections = sections;
        this.emissionsByScope = emissionsByScope;
        this.status = "DRAFT";
        this.generatedAt = Instant.now();
    }

    /** 보고서 생성 팩토리 */
    public static DisclosureReport create(CreateReportCommand cmd) {
        List<ReportSection> sections = ReportBuilder.buildKssb2Sections(
            cmd.emissionsByScope(), null);
        return new DisclosureReport(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.framework(),
            sections, cmd.emissionsByScope());
    }

    /** DRAFT → SUBMITTED */
    public void submit(UUID actorId) {
        if (!"DRAFT".equals(this.status)) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "DRAFT 상태에서만 제출할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = "SUBMITTED";
        this.submittedAt = Instant.now();
    }

    /** SUBMITTED → APPROVED */
    public void approve(UUID actorId) {
        if (!"SUBMITTED".equals(this.status)) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "SUBMITTED 상태에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = "APPROVED";
        this.approvedAt = Instant.now();
        this.approvedBy = actorId;
    }

    /** SUBMITTED → REJECTED (reason 필수, 공백 불가) */
    public void reject(UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new EsgException(EsgErrorCode.REJECTION_REASON_REQUIRED, "반려 사유는 필수입니다");
        }
        if (!"SUBMITTED".equals(this.status)) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "SUBMITTED 상태에서만 반려할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = "REJECTED";
        this.rejectionReason = reason;
    }

    /** Scope 1+2+3 총 배출량 */
    public BigDecimal totalEmission() {
        return emissionsByScope.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Getters
    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID entityId() { return entityId; }
    public int reportingYear() { return reportingYear; }
    public String framework() { return framework; }
    public List<ReportSection> sections() { return sections; }
    public Map<String, BigDecimal> emissionsByScope() { return emissionsByScope; }
    public String status() { return status; }
    public Instant submittedAt() { return submittedAt; }
    public Instant approvedAt() { return approvedAt; }
    public UUID approvedBy() { return approvedBy; }
    public String rejectionReason() { return rejectionReason; }
    public Instant generatedAt() { return generatedAt; }
}
