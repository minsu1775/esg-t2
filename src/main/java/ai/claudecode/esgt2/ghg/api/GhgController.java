package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
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
}
