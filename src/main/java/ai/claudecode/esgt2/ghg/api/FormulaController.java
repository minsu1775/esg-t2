package ai.claudecode.esgt2.ghg.api;

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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ghg/formulas")
@RequiredArgsConstructor
@Tag(name = "Formula", description = "산식 버전 관리 API")
public class FormulaController {

    private final FormulaVersionService formulaVersionService;

    @Operation(summary = "산식 등록",
               description = "YAML 산식을 등록합니다. test_cases 게이트 통과 시만 ACTIVE 활성화됩니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "test_cases 실패 또는 YAML 파싱 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<FormulaVersionResponse> register(
            Authentication authentication,
            @RequestBody @Valid RegisterFormulaRequest request) {
        var auth = (JwtAuthentication) authentication;
        var response = formulaVersionService.register(auth.getPrincipal(), request);
        return ResponseEntity.created(
            URI.create("/api/v1/ghg/formulas/" + response.id())).body(response);
    }

    @Operation(summary = "산식 버전 목록 조회",
               description = "동일 code의 전체 버전 이력을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<List<FormulaVersionResponse>> findAll(
            Authentication authentication,
            @RequestParam String code) {
        return ResponseEntity.ok(formulaVersionService.findAll(code));
    }

    @Operation(summary = "산식 비활성화",
               description = "특정 버전을 INACTIVE로 전환합니다. DELETE 없음 — P1 재현성 보호.")
    @ApiResponse(responseCode = "200", description = "비활성화 성공")
    @ApiResponse(responseCode = "404", description = "버전 없음")
    @PostMapping("/{formulaVersionId}/deactivate")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<FormulaVersionResponse> deactivate(
            Authentication authentication,
            @PathVariable UUID formulaVersionId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            formulaVersionService.deactivate(auth.getPrincipal(), formulaVersionId));
    }

    @Operation(summary = "산식 변경 영향 조회",
               description = "산식 버전의 ghgCategory에 해당하는 활동 데이터 건수와 법인 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "버전 없음")
    @GetMapping("/{formulaVersionId}/impact")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<FormulaImpactResponse> getImpact(
            Authentication authentication,
            @PathVariable UUID formulaVersionId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            formulaVersionService.getImpact(auth.getTenantId(), formulaVersionId));
    }
}
