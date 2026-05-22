package ai.claudecode.esgt2.rpt.api;

import java.util.List;
import java.util.UUID;

public interface ReportService {

    /** 보고서 생성 (배출량 데이터 집계 + KSSB2 섹션 조립) (T-7-05, T-7-06) */
    ReportResponse createReport(UUID tenantId, UUID actorId, CreateReportRequest request);

    /** 보고서 조회 */
    ReportResponse getReport(UUID tenantId, UUID reportId);

    /** 법인·연도 기준 보고서 목록 조회 */
    List<ReportResponse> findReports(UUID tenantId, UUID entityId, int reportingYear);

    /** 보고서 제출 (DRAFT → SUBMITTED) */
    ReportResponse submitReport(UUID tenantId, UUID actorId, UUID reportId);

    /** 보고서 승인 (SUBMITTED → APPROVED) (T-7-08) */
    ReportResponse approveReport(UUID tenantId, UUID actorId, UUID reportId);

    /** 보고서 반려 (SUBMITTED → REJECTED) (T-7-12) */
    ReportResponse rejectReport(UUID tenantId, UUID actorId, UUID reportId, String reason);

    /** PDF 다운로드용 바이트 배열 생성 (T-7-09) */
    byte[] generatePdf(UUID tenantId, UUID reportId);

    /** 보고서가 APPROVED 상태인지 확인 — vw 모듈에서 스냅샷 생성 전 사용 (T-7-04) */
    boolean isApproved(UUID tenantId, UUID reportId);
}
