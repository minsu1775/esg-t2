package ai.claudecode.esgt2.rpt.internal;

import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.rpt.api.CreateReportRequest;
import ai.claudecode.esgt2.rpt.api.ReportResponse;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.rpt.domain.CreateReportCommand;
import ai.claudecode.esgt2.rpt.domain.DisclosureReport;
import ai.claudecode.esgt2.rpt.domain.ReportBuilder;
import ai.claudecode.esgt2.rpt.domain.ReportSection;
import ai.claudecode.esgt2.rpt.infra.DisclosureReportJpaEntity;
import ai.claudecode.esgt2.rpt.infra.DisclosureReportMapper;
import ai.claudecode.esgt2.rpt.infra.DisclosureReportRepository;
import ai.claudecode.esgt2.rpt.infra.PdfReportRenderer;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultReportService implements ReportService {

    private final DisclosureReportRepository reportRepository;
    private final GhgService ghgService;
    private final PdfReportRenderer pdfRenderer;

    @Override
    @Transactional
    @Auditable(action = "REPORT_CREATED")
    public ReportResponse createReport(UUID tenantId, UUID actorId, CreateReportRequest request) {
        // 1. 배출량 기록 조회 (ghg.api 인터페이스를 통해 모듈 경계 준수)
        var records = ghgService.findEmissionRecords(
            tenantId, request.entityId(), request.reportingYear());

        // 2. 스코프별 집계
        Map<String, BigDecimal> emissionsByScope = aggregateByScope(records);

        // 3. 전년 APPROVED 보고서 조회 (YoY용)
        Map<String, BigDecimal> prevYearEmissions = getPreviousYearEmissions(
            tenantId, request.entityId(), request.reportingYear() - 1);

        // 4. 도메인 생성
        var cmd = new CreateReportCommand(
            tenantId, request.entityId(), request.reportingYear(),
            request.framework(), emissionsByScope);
        var domain = DisclosureReport.create(cmd);

        // 5. YoY 섹션 재조립 (전년 데이터 포함)
        var sections = ReportBuilder.buildKssb2Sections(emissionsByScope, prevYearEmissions);

        // 6. 저장
        var entity = DisclosureReportMapper.toEntity(domain);
        var saved = reportRepository.save(entity);

        return toResponse(saved, sections, emissionsByScope);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID tenantId, UUID reportId) {
        var entity = findEntity(tenantId, reportId);
        var emissionsByScope = entity.emissionsByScopeFromContent();
        var sections = ReportBuilder.buildKssb2Sections(emissionsByScope, null);
        return toResponse(entity, sections, emissionsByScope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponse> findReports(UUID tenantId, UUID entityId, int reportingYear) {
        return reportRepository
            .findByTenantIdAndEntityIdAndReportingYear(tenantId, entityId, reportingYear)
            .stream()
            .map(e -> {
                var em = e.emissionsByScopeFromContent();
                return toResponse(e, ReportBuilder.buildKssb2Sections(em, null), em);
            })
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "REPORT_SUBMITTED")
    public ReportResponse submitReport(UUID tenantId, UUID actorId, UUID reportId) {
        var entity = findEntity(tenantId, reportId);
        var domain = toDomainForSubmit(entity);
        domain.submit(actorId);
        entity.updateFromDomain(domain.status(), domain.submittedAt(),
            domain.approvedAt(), domain.approvedBy(), domain.rejectionReason());
        var saved = reportRepository.save(entity);
        var em = saved.emissionsByScopeFromContent();
        return toResponse(saved, ReportBuilder.buildKssb2Sections(em, null), em);
    }

    @Override
    @Transactional
    @Auditable(action = "REPORT_APPROVED")
    public ReportResponse approveReport(UUID tenantId, UUID actorId, UUID reportId) {
        var entity = findEntity(tenantId, reportId);
        // 엔티티 상태 직접 가드 — SUBMITTED여야만 승인 가능
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "SUBMITTED 상태에서만 승인할 수 있습니다. 현재 상태: " + entity.getStatus());
        }
        var domain = toDomainAsSubmitted(entity);
        domain.approve(actorId);
        entity.updateFromDomain(domain.status(), domain.submittedAt(),
            domain.approvedAt(), domain.approvedBy(), domain.rejectionReason());
        var saved = reportRepository.save(entity);
        var em = saved.emissionsByScopeFromContent();
        return toResponse(saved, ReportBuilder.buildKssb2Sections(em, null), em);
    }

    @Override
    @Transactional
    @Auditable(action = "REPORT_REJECTED")
    public ReportResponse rejectReport(UUID tenantId, UUID actorId, UUID reportId, String reason) {
        var entity = findEntity(tenantId, reportId);
        // 엔티티 상태 직접 가드
        if (!"SUBMITTED".equals(entity.getStatus())) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "SUBMITTED 상태에서만 반려할 수 있습니다. 현재 상태: " + entity.getStatus());
        }
        var domain = toDomainAsSubmitted(entity);
        domain.reject(actorId, reason);
        entity.updateFromDomain(domain.status(), domain.submittedAt(),
            domain.approvedAt(), domain.approvedBy(), domain.rejectionReason());
        var saved = reportRepository.save(entity);
        var em = saved.emissionsByScopeFromContent();
        return toResponse(saved, ReportBuilder.buildKssb2Sections(em, null), em);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID tenantId, UUID reportId) {
        var entity = findEntity(tenantId, reportId);
        var emissionsByScope = entity.emissionsByScopeFromContent();
        var sections = ReportBuilder.buildKssb2Sections(emissionsByScope, null);
        BigDecimal total = emissionsByScope.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return pdfRenderer.render(
            entity.getEntityId().toString(),
            entity.getReportingYear(),
            entity.getFramework(),
            sections, total);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isApproved(UUID tenantId, UUID reportId) {
        return reportRepository.findByIdAndTenantId(reportId, tenantId)
            .map(e -> "APPROVED".equals(e.getStatus()))
            .orElse(false);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private DisclosureReportJpaEntity findEntity(UUID tenantId, UUID reportId) {
        return reportRepository.findByIdAndTenantId(reportId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "disclosure_report not found: " + reportId));
    }

    /**
     * DRAFT 상태 엔티티 → 도메인 (submit 호출 준비).
     * 엔티티가 DRAFT여야 함 — submitReport에서 사용.
     */
    private DisclosureReport toDomainForSubmit(DisclosureReportJpaEntity e) {
        var emissionsByScope = e.emissionsByScopeFromContent();
        var cmd = new CreateReportCommand(
            e.getTenantId(), e.getEntityId(), e.getReportingYear(),
            e.getFramework(), emissionsByScope);
        return DisclosureReport.create(cmd);   // DRAFT 상태로 생성
    }

    /**
     * SUBMITTED 상태로 도메인을 복원.
     * approve/reject 전 내부 호출용. 엔티티 상태 가드가 선행됨.
     */
    private DisclosureReport toDomainAsSubmitted(DisclosureReportJpaEntity e) {
        var domain = toDomainForSubmit(e);
        // 내부 복원 — 외부 actorId는 없으므로 랜덤 UUID 사용 (submittedAt 복원 목적)
        domain.submit(UUID.randomUUID());
        return domain;   // SUBMITTED 상태
    }

    private Map<String, BigDecimal> aggregateByScope(List<EmissionRecordResponse> records) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (var r : records) {
            result.merge(r.scope(), r.rawEmission(), BigDecimal::add);
        }
        return result;
    }

    private Map<String, BigDecimal> getPreviousYearEmissions(
            UUID tenantId, UUID entityId, int prevYear) {
        return reportRepository
            .findFirstByTenantIdAndEntityIdAndReportingYearAndStatus(
                tenantId, entityId, prevYear, "APPROVED")
            .map(DisclosureReportJpaEntity::emissionsByScopeFromContent)
            .orElse(null);
    }

    private ReportResponse toResponse(DisclosureReportJpaEntity e,
                                       List<ReportSection> sections,
                                       Map<String, BigDecimal> emissionsByScope) {
        BigDecimal total = emissionsByScope.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var sectionDtos = sections.stream()
            .map(s -> new ReportResponse.SectionDto(
                s.itemCode(), s.title(), s.value(), s.yoyDelta()))
            .toList();
        return new ReportResponse(
            e.getId(), e.getEntityId(), e.getReportingYear(), e.getFramework(),
            e.getStatus(), emissionsByScope, total, sectionDtos, e.getGeneratedAt(),
            e.getApprovedAt());
    }
}
