package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Verification Workspace", description = "외부 검증 워크스페이스 API (T-8-07~10)")
@RestController
@RequestMapping("/api/v1/vw/snapshots")
@RequiredArgsConstructor
public class VwController {

    private final SnapshotService snapshotService;

    @Operation(summary = "검증 스냅샷 생성",
        description = "APPROVED 보고서로부터 SHA-256 불변 스냅샷을 생성합니다.")
    @ApiResponse(responseCode = "201", description = "스냅샷 생성 성공")
    @ApiResponse(responseCode = "400", description = "미승인 보고서 (REPORT_NOT_APPROVED)")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    @PostMapping
    public ResponseEntity<SnapshotResponse> createSnapshot(
            @RequestParam UUID tenantId,
            @RequestParam UUID reportId,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(201)
            .body(snapshotService.createSnapshot(tenantId, actorId, reportId));
    }

    @Operation(summary = "스냅샷 조회",
        description = "VERIFIER는 자신에게 지정된 스냅샷만 조회 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 불가 (VERIFIER 격리)")
    @ApiResponse(responseCode = "404", description = "스냅샷 미존재")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @GetMapping("/{snapshotId}")
    public ResponseEntity<SnapshotResponse> getSnapshot(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId) {
        return ResponseEntity.ok(snapshotService.getSnapshot(tenantId, snapshotId));
    }

    @Operation(summary = "코멘트 작성",
        description = "검증 의견을 스냅샷에 기록합니다.")
    @ApiResponse(responseCode = "201", description = "코멘트 등록 성공")
    @ApiResponse(responseCode = "403", description = "접근 불가")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @PostMapping("/{snapshotId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId,
            @RequestBody AddCommentRequest request,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(201)
            .body(snapshotService.addComment(tenantId, actorId, snapshotId, request.body()));
    }

    @Operation(summary = "코멘트 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @GetMapping("/{snapshotId}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId) {
        return ResponseEntity.ok(snapshotService.listComments(tenantId, snapshotId));
    }

    @Operation(summary = "검증 완료 서명",
        description = "검증인이 스냅샷 검토를 완료하고 서명합니다. 스냅샷당 1회만 허용.")
    @ApiResponse(responseCode = "204", description = "서명 완료")
    @ApiResponse(responseCode = "400", description = "이미 서명된 스냅샷")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PreAuthorize("hasRole('VERIFIER') or hasRole('ESG_MANAGER')")
    @PostMapping("/{snapshotId}/sign")
    public ResponseEntity<Void> signSnapshot(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId,
            @RequestBody(required = false) SignRequest request,
            Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        String note = request != null ? request.note() : null;
        snapshotService.signSnapshot(tenantId, actorId, snapshotId, note);
        return ResponseEntity.noContent().build();
    }

    /** 코멘트 요청 DTO */
    public record AddCommentRequest(String body) {}

    /** 서명 요청 DTO (note 선택) */
    public record SignRequest(String note) {}
}
