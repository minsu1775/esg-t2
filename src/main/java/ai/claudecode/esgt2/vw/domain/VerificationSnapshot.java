package ai.claudecode.esgt2.vw.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 외부 검증 스냅샷 도메인 객체.
 * SHA-256 해시로 무결성을 보장하는 불변 record.
 * 변경 메서드 미제공 — 도메인 레벨 불변성 보장.
 */
public record VerificationSnapshot(
    UUID id,
    UUID tenantId,
    UUID reportId,
    String snapshotHash,     // SHA-256 hex (64자)
    String snapshotDataJson, // JSONB 원문
    Instant createdAt,
    Instant frozenAt
) {
    /**
     * 신규 스냅샷 생성 팩토리.
     *
     * @param tenantId         테넌트 ID
     * @param reportId         스냅샷 대상 보고서 ID
     * @param snapshotDataJson 보고서 내용 JSON 직렬화 결과
     * @return 신규 VerificationSnapshot (SHA-256 해시 포함)
     */
    public static VerificationSnapshot create(UUID tenantId, UUID reportId,
                                               String snapshotDataJson) {
        String hash = sha256(snapshotDataJson);
        Instant now = Instant.now();
        return new VerificationSnapshot(
            UUID.randomUUID(), tenantId, reportId,
            hash, snapshotDataJson, now, now
        );
    }

    /**
     * SHA-256 해시 계산 (UTF-8 인코딩, 소문자 hex 64자).
     */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JVM 표준 알고리즘 — 미지원 환경 없음
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR,
                "SHA-256 알고리즘 초기화 실패: " + e.getMessage());
        }
    }
}
