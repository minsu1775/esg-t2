package ai.claudecode.esgt2.supply.api;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supply")
@RequiredArgsConstructor
@Tag(name = "Supply", description = "공급업체 포털 API")
public class SupplierController {

    private final SupplierService supplierService;

    // ── 초대 / 계정 활성화 ──────────────────────────────────────────────────

    @Operation(summary = "공급업체 초대", description = "이메일로 공급업체를 초대하고 활성화 링크를 발송합니다.")
    @ApiResponse(responseCode = "201", description = "초대 완료")
    @ApiResponse(responseCode = "403", description = "권한 없음 (TENANT_ADMIN 필요)")
    @PostMapping("/suppliers/invite")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<SupplierInvitationResponse> inviteSupplier(
            Authentication authentication,
            @RequestBody @Valid InviteSupplierRequest request) {
        var auth = (JwtAuthentication) authentication;
        var response = supplierService.inviteSupplier(
            auth.getTenantId(), auth.getPrincipal(), request);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "계정 활성화",
               description = "초대 토큰으로 공급업체 계정을 활성화합니다. JWT 불필요.")
    @ApiResponse(responseCode = "204", description = "활성화 완료")
    @ApiResponse(responseCode = "400", description = "만료·사용됨·잘못된 토큰")
    @PostMapping("/suppliers/activate")
    @PreAuthorize("permitAll()")
    // @PreAuthorize 면제: 초대 토큰이 인증 역할을 대체한다 (03-security.md Webhook 항목과 동일 패턴).
    // SecurityConfig에 permitAll 등록됨.
    public ResponseEntity<Void> activateAccount(@RequestBody @Valid ActivateSupplierRequest request) {
        supplierService.activateAccount(request);
        return ResponseEntity.noContent().build();
    }

    // ── 공급업체 데이터 입력 / 제출 ─────────────────────────────────────────

    @Operation(summary = "활동 데이터 등록 (공급업체)",
               description = "SUPPLIER는 자신의 법인(entityId) 데이터만 등록 가능합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "403", description = "타 법인 접근 금지")
    @PostMapping("/entities/{entityId}/activity-data")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ActivityDataResponse> submitData(
            Authentication authentication,
            @PathVariable UUID entityId,
            @RequestBody @Valid SupplierDataRequest request) {
        var auth = (JwtAuthentication) authentication;
        // T-6-09: 크로스-엔티티 방어 — SUPPLIER는 JWT.entityId와 일치하는 법인만 접근 가능
        if (!entityId.equals(auth.getEntityId())) {
            return ResponseEntity.status(403).build();
        }
        var response = supplierService.submitData(
            auth.getTenantId(), auth.getPrincipal(), entityId, request);
        return ResponseEntity.created(
            URI.create("/api/v1/supply/entities/" + entityId +
                "/activity-data/" + response.id()))
            .body(response);
    }

    @Operation(summary = "데이터 검토 요청",
               description = "DRAFT 상태 데이터를 PENDING으로 전환합니다.")
    @ApiResponse(responseCode = "200", description = "전환 성공")
    @PostMapping("/activity-data/{activityDataId}/submit")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ActivityDataResponse> submitForReview(
            Authentication authentication,
            @PathVariable UUID activityDataId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(supplierService.submitForReview(
            auth.getTenantId(), auth.getPrincipal(), activityDataId));
    }

    // ── ESG_MANAGER 승인 / 반려 ─────────────────────────────────────────────

    @Operation(summary = "활동 데이터 승인", description = "PENDING → APPROVED")
    @ApiResponse(responseCode = "200", description = "승인 완료")
    @PostMapping("/activity-data/{activityDataId}/approve")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ActivityDataResponse> approveData(
            Authentication authentication,
            @PathVariable UUID activityDataId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(supplierService.approveData(
            auth.getTenantId(), auth.getPrincipal(), activityDataId));
    }

    @Operation(summary = "활동 데이터 반려", description = "PENDING → REJECTED")
    @ApiResponse(responseCode = "200", description = "반려 완료")
    @ApiResponse(responseCode = "400", description = "반려 사유 누락")
    @PostMapping("/activity-data/{activityDataId}/reject")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ActivityDataResponse> rejectData(
            Authentication authentication,
            @PathVariable UUID activityDataId,
            @RequestParam String reason) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(supplierService.rejectData(
            auth.getTenantId(), auth.getPrincipal(), activityDataId, reason));
    }
}
