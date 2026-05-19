package ai.claudecode.esgt2.entity.api;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
@Tag(name = "Legal Entities", description = "법인 관리 API")
public class EntityController {

    private final EntityManagementService entityManagementService;

    @Operation(summary = "법인 등록", description = "새 법인을 등록합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "입력값 유효성 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음 (TENANT_ADMIN 필요)")
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<EntityResponse> create(
            @AuthenticationPrincipal JwtAuthentication auth,
            @RequestBody @Valid CreateEntityRequest request) {
        UUID tenantId = auth.getTenantId();
        EntityResponse response = entityManagementService.create(tenantId, request);
        return ResponseEntity.created(URI.create("/api/v1/entities/" + response.id()))
            .body(response);
    }

    @Operation(summary = "법인 목록 조회", description = "테넌트의 활성 법인 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EntityResponse>> findAll(
            @AuthenticationPrincipal JwtAuthentication auth) {
        return ResponseEntity.ok(entityManagementService.findAll(auth.getTenantId()));
    }

    @Operation(summary = "법인 관계 설정", description = "부모 법인과 자식 법인 간의 지분 관계를 설정합니다.")
    @ApiResponse(responseCode = "201", description = "관계 설정 성공")
    @ApiResponse(responseCode = "400", description = "유효하지 않은 관계 (순환 참조 등)")
    @ApiResponse(responseCode = "403", description = "권한 없음 (TENANT_ADMIN 필요)")
    @PutMapping("/{parentId}/relationships")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<RelationshipResponse> setRelationship(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID parentId,
            @RequestBody @Valid SetRelationshipRequest request) {
        RelationshipResponse response = entityManagementService.setRelationship(
            auth.getTenantId(), parentId, request);
        return ResponseEntity.status(201).body(response);
    }
}
