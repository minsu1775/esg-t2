package ai.claudecode.esgt2.rpt.api;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "공시 보고서 관리 API")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "보고서 생성",
               description = "GHG 배출량 데이터를 집계하여 KSSB2 공시 보고서를 생성합니다.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ReportResponse> createReport(
            Authentication authentication,
            @RequestBody @Valid CreateReportRequest request) {
        var auth = (JwtAuthentication) authentication;
        var response = reportService.createReport(auth.getTenantId(), auth.getPrincipal(), request);
        return ResponseEntity.created(
            URI.create("/api/v1/reports/" + response.id())).body(response);
    }

    @Operation(summary = "보고서 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "보고서 없음")
    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER', 'VERIFIER')")
    public ResponseEntity<ReportResponse> getReport(
            Authentication authentication,
            @PathVariable UUID reportId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(reportService.getReport(auth.getTenantId(), reportId));
    }

    @Operation(summary = "보고서 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<List<ReportResponse>> findReports(
            Authentication authentication,
            @RequestParam UUID entityId,
            @RequestParam int reportingYear) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            reportService.findReports(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "보고서 제출 (DRAFT → SUBMITTED)")
    @ApiResponse(responseCode = "200", description = "제출 성공")
    @PostMapping("/{reportId}/submit")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ReportResponse> submitReport(
            Authentication authentication,
            @PathVariable UUID reportId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            reportService.submitReport(auth.getTenantId(), auth.getPrincipal(), reportId));
    }

    @Operation(summary = "보고서 승인 (SUBMITTED → APPROVED)",
               description = "보고서를 승인합니다. 승인 후 검증 스냅샷 생성이 가능해집니다.")
    @ApiResponse(responseCode = "200", description = "승인 성공")
    @PostMapping("/{reportId}/approve")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ReportResponse> approveReport(
            Authentication authentication,
            @PathVariable UUID reportId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            reportService.approveReport(auth.getTenantId(), auth.getPrincipal(), reportId));
    }

    @Operation(summary = "보고서 반려 (SUBMITTED → REJECTED)")
    @ApiResponse(responseCode = "200", description = "반려 성공")
    @ApiResponse(responseCode = "400", description = "반려 사유 누락")
    @PostMapping("/{reportId}/reject")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ReportResponse> rejectReport(
            Authentication authentication,
            @PathVariable UUID reportId,
            @RequestParam String reason) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            reportService.rejectReport(auth.getTenantId(), auth.getPrincipal(), reportId, reason));
    }

    @Operation(summary = "PDF 보고서 다운로드",
               description = "보고서를 PDF 형식으로 다운로드합니다.")
    @ApiResponse(responseCode = "200", description = "PDF 생성 성공")
    @ApiResponse(responseCode = "404", description = "보고서 없음")
    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER', 'VERIFIER')")
    public ResponseEntity<byte[]> downloadPdf(
            Authentication authentication,
            @PathVariable UUID reportId) {
        var auth = (JwtAuthentication) authentication;
        byte[] pdf = reportService.generatePdf(auth.getTenantId(), reportId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"report-" + reportId + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
