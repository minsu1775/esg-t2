package ai.claudecode.esgt2.vw.internal;

import ai.claudecode.esgt2.rpt.api.ReportResponse;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.vw.api.CommentResponse;
import ai.claudecode.esgt2.vw.api.SnapshotResponse;
import ai.claudecode.esgt2.vw.api.SnapshotService;
import ai.claudecode.esgt2.vw.domain.VerificationComment;
import ai.claudecode.esgt2.vw.domain.VerificationSignature;
import ai.claudecode.esgt2.vw.domain.VerificationSnapshot;
import ai.claudecode.esgt2.vw.infra.VerificationCommentJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationCommentRepository;
import ai.claudecode.esgt2.vw.infra.VerificationSignatureJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationSignatureRepository;
import ai.claudecode.esgt2.vw.infra.VerificationSnapshotJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultSnapshotService implements SnapshotService {

    private final ReportService reportService;
    private final VerificationSnapshotRepository snapshotRepository;
    private final VerificationCommentRepository commentRepository;
    private final VerificationSignatureRepository signatureRepository;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────────
    // T-8-07: 스냅샷 생성 (APPROVED 보고서만)
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_CREATED")
    public SnapshotResponse createSnapshot(UUID tenantId, UUID actorId, UUID reportId) {
        // T-8-05: APPROVED 게이트
        if (!reportService.isApproved(tenantId, reportId)) {
            throw new EsgException(EsgErrorCode.REPORT_NOT_APPROVED,
                "APPROVED 상태의 보고서만 스냅샷을 생성할 수 있습니다.");
        }

        ReportResponse report = reportService.getReport(tenantId, reportId);
        String snapshotDataJson = buildSnapshotJson(report);

        VerificationSnapshot domain = VerificationSnapshot.create(tenantId, reportId, snapshotDataJson);
        var entity = VerificationSnapshotJpaEntity.fromDomain(domain);
        snapshotRepository.save(entity);

        log.info("스냅샷 생성 완료: snapshotId={}, reportId={}, hash={}",
            domain.id(), reportId, domain.snapshotHash());

        boolean signed = signatureRepository.existsBySnapshotIdAndTenantId(domain.id(), tenantId);
        return toResponse(domain, signed);
    }

    // ──────────────────────────────────────────────
    // T-8-07: 스냅샷 조회
    // ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public SnapshotResponse getSnapshot(UUID tenantId, UUID snapshotId) {
        var entity = snapshotRepository.findByIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId));

        boolean signed = signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId);
        return toResponse(entity.toDomain(), signed);
    }

    // ──────────────────────────────────────────────
    // T-8-09: 코멘트 작성
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_COMMENT_ADDED")
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public CommentResponse addComment(UUID tenantId, UUID actorId,
                                       UUID snapshotId, String body) {
        if (!snapshotRepository.existsByIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId);
        }

        VerificationComment domain = VerificationComment.create(snapshotId, tenantId, actorId, body);
        commentRepository.save(VerificationCommentJpaEntity.fromDomain(domain));

        return new CommentResponse(domain.id(), domain.snapshotId(), domain.authorId(),
            domain.body(), domain.createdAt());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public List<CommentResponse> listComments(UUID tenantId, UUID snapshotId) {
        return commentRepository
            .findBySnapshotIdAndTenantIdOrderByCreatedAtAsc(snapshotId, tenantId)
            .stream()
            .map(e -> {
                var d = e.toDomain();
                return new CommentResponse(d.id(), d.snapshotId(), d.authorId(),
                    d.body(), d.createdAt());
            })
            .toList();
    }

    // ──────────────────────────────────────────────
    // T-8-10: 검증 완료 서명
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_SIGNED")
    @PreAuthorize("hasRole('VERIFIER') or hasRole('ESG_MANAGER')")
    public void signSnapshot(UUID tenantId, UUID actorId, UUID snapshotId, String note) {
        if (!snapshotRepository.existsByIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId);
        }
        if (signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "이미 서명이 완료된 스냅샷입니다: " + snapshotId);
        }

        VerificationSignature domain =
            VerificationSignature.create(snapshotId, tenantId, actorId, note);
        signatureRepository.save(VerificationSignatureJpaEntity.fromDomain(domain));

        log.info("스냅샷 서명 완료: snapshotId={}, signedBy={}", snapshotId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSigned(UUID tenantId, UUID snapshotId) {
        return signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId);
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    /**
     * 보고서 데이터를 스냅샷 JSON으로 직렬화.
     * LinkedHashMap으로 필드 순서를 고정하여 SHA-256 재현성 보장.
     */
    private String buildSnapshotJson(ReportResponse report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.id().toString());
        payload.put("entityId", report.entityId().toString());
        payload.put("reportingYear", report.reportingYear());
        payload.put("framework", report.framework());
        payload.put("status", report.status());
        payload.put("totalEmission", report.totalEmission() != null
            ? report.totalEmission().toPlainString() : null);
        payload.put("sections", report.sections().stream()
            .map(s -> Map.of(
                "itemCode", s.itemCode(),
                "value", s.value() != null ? s.value().toPlainString() : "0",
                "yoyDelta", s.yoyDelta() != null ? s.yoyDelta().toPlainString() : ""
            ))
            .toList());
        payload.put("approvedAt", report.approvedAt() != null
            ? report.approvedAt().toString() : null);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR,
                "스냅샷 JSON 직렬화 실패: " + e.getMessage());
        }
    }

    private SnapshotResponse toResponse(VerificationSnapshot domain, boolean signed) {
        return new SnapshotResponse(
            domain.id(), domain.tenantId(), domain.reportId(),
            domain.snapshotHash(), domain.createdAt(), signed
        );
    }
}
