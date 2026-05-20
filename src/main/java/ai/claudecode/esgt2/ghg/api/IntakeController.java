package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intake")
@RequiredArgsConstructor
@Tag(name = "Intake", description = "대량 활동 데이터 수집 API (CSV 업로드, Webhook)")
public class IntakeController {

    private static final UUID SYSTEM_WEBHOOK_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IntakeService intakeService;

    @Value("${intake.webhook.hmac-secret}")
    private String webhookHmacSecret;

    @Operation(summary = "CSV 활동 데이터 업로드",
               description = "CSV 파일로 ActivityData를 일괄 등록합니다. 중복 행은 건너뜁니다.")
    @ApiResponse(responseCode = "200", description = "처리 결과 (오류 행 포함)")
    @ApiResponse(responseCode = "400", description = "CSV 파싱 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping(value = "/entities/{entityId}/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<CsvUploadResponse> uploadCsv(
            @AuthenticationPrincipal JwtAuthentication auth,
            @Parameter(description = "법인 ID") @PathVariable UUID entityId,
            @RequestParam("file") MultipartFile file) {
        var result = intakeService.uploadCsv(auth.getTenantId(), entityId, file.getResource());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Webhook 활동 데이터 수신",
               description = "외부 ERP/시스템에서 HMAC-SHA256 서명으로 데이터를 전송합니다.")
    @ApiResponse(responseCode = "200", description = "처리 결과")
    @ApiResponse(responseCode = "401", description = "Webhook 서명 불일치")
    // @PreAuthorize 면제: JWT 없는 외부 시스템 호출. HMAC-SHA256 서명 검증이 인증을 대체한다 (03-security.md Webhook 항목).
    // SecurityConfig에 permitAll 등록됨.
    @PostMapping("/tenants/{tenantId}/webhook")
    public ResponseEntity<CsvUploadResponse> receiveWebhook(
            @Parameter(description = "테넌트 ID") @PathVariable UUID tenantId,
            @RequestHeader("X-Hub-Signature-256") String signature,
            HttpServletRequest request) throws Exception {

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8);

        if (!isValidSignature(rawBody, signature)) {
            throw new EsgException(EsgErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        List<WebhookActivityDataItem> items = OBJECT_MAPPER.readValue(
            rawBody, new TypeReference<>() {});

        // JWT 없는 Webhook 호출에도 @Auditable AOP가 감사 로그를 남기도록 system actor 설정
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(SYSTEM_WEBHOOK_ACTOR, tenantId, List.of("WEBHOOK")));

        var result = intakeService.receiveWebhook(tenantId, items);
        return ResponseEntity.ok(result);
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                webhookHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
