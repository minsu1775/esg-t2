package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ghg")
@RequiredArgsConstructor
@Tag(name = "GHG", description = "GHG 배출량 관리 API")
public class GhgController {

    private final GhgService ghgService;
    private final ConsolidationService consolidationService;
    private final Scope3Service scope3Service;

    @Operation(summary = "활동 데이터 등록", description = "Scope 1/2 활동 데이터를 등록합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력값 유효성 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping("/entities/{entityId}/activity-data")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ActivityDataResponse> createActivityData(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestBody @Valid CreateActivityDataRequest request) {
        var response = ghgService.createActivityData(auth.getTenantId(), entityId, request);
        return ResponseEntity.created(
            URI.create("/api/v1/ghg/entities/" + entityId + "/activity-data/" + response.id()))
            .body(response);
    }

    @Operation(summary = "활동 데이터 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/entities/{entityId}/activity-data")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<List<ActivityDataResponse>> findActivityData(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestParam int reportingYear) {
        return ResponseEntity.ok(ghgService.findActivityData(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "배출량 산출", description = "등록된 활동 데이터로 Scope 1/2 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @ApiResponse(responseCode = "404", description = "배출계수 없음")
    @PostMapping("/entities/{entityId}/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateEmissions(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestParam int reportingYear) {
        var records = ghgService.calculateEmissions(auth.getTenantId(), entityId, reportingYear);
        return ResponseEntity.status(201).body(records);
    }

    @Operation(summary = "배출량 기록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/entities/{entityId}/emission-records")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER', 'VERIFIER')")
    public ResponseEntity<List<EmissionRecordResponse>> findEmissionRecords(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestParam int reportingYear) {
        return ResponseEntity.ok(ghgService.findEmissionRecords(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "연결 집계 산출",
        description = "다법인 지분 구조 기반 연결 GHG 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 연결 방법(EQUITY|OPERATIONAL_CONTROL) 또는 순환 지분 구조")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "루트 법인 없음")
    @PostMapping("/entities/{rootEntityId}/consolidations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ConsolidationResponse> consolidate(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID rootEntityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear,
            @Parameter(description = "연결 방법 (EQUITY | OPERATIONAL_CONTROL)", example = "EQUITY")
            @RequestParam ConsolidationMethod method) {
        var response = consolidationService.consolidate(
            auth.getTenantId(), rootEntityId, reportingYear, method);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "연결 집계 이력 조회", description = "루트 법인 기준 연결 집계 이력을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/entities/{rootEntityId}/consolidations")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<List<ConsolidationResponse>> findConsolidations(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID rootEntityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.ok(
            consolidationService.findConsolidations(auth.getTenantId(), rootEntityId, reportingYear));
    }

    @Operation(summary = "Scope 3 Cat.1 배출량 산출",
               description = "구매재화·서비스 지출 기반 Scope 3 Cat.1 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping("/entities/{entityId}/scope3/cat1/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat1(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat1(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 Cat.2 배출량 산출",
               description = "자본재 취득액 기반 Scope 3 Cat.2 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @PostMapping("/entities/{entityId}/scope3/cat2/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat2(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat2(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 Cat.11 배출량 산출",
               description = "판매제품 사용 생애주기 귀속 방식으로 Scope 3 Cat.11 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @PostMapping("/entities/{entityId}/scope3/cat11/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat11(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat11(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 커버리지 보고서 생성",
               description = "배출량 기반 95% 임계값 판단. 제외 카테고리 추정치를 요청 본문에 포함.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/entities/{entityId}/scope3/coverage-report")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<Scope3CoverageResponse> generateCoverageReport(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestBody @Valid Scope3CoverageRequest request) {
        return ResponseEntity.status(201)
            .body(scope3Service.generateCoverageReport(
                auth.getTenantId(), entityId, request.reportingYear(), request));
    }

    @Operation(summary = "Scope 3 커버리지 보고서 조회",
               description = "가장 최근 생성된 Scope 3 커버리지 보고서를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "보고서 없음")
    @GetMapping("/entities/{entityId}/scope3/coverage-report")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<Scope3CoverageResponse> getCoverageReport(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.ok(
            scope3Service.getCoverageReport(auth.getTenantId(), entityId, reportingYear));
    }
}
