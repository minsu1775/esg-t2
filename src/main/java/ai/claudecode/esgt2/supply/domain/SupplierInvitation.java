package ai.claudecode.esgt2.supply.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 공급업체 초대 도메인 레코드.
 * 상태 기계: PENDING → ACCEPTED 또는 EXPIRED.
 */
public record SupplierInvitation(
    UUID id,
    UUID tenantId,
    UUID entityId,
    String email,
    UUID token,
    String status,
    UUID invitedBy,
    OffsetDateTime expiresAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime createdAt
) {
    /** 새 초대 생성 팩토리. 유효 기간 7일. */
    public static SupplierInvitation create(UUID tenantId, UUID entityId,
                                             String email, UUID invitedBy) {
        if (email == null || email.isBlank()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "초대 이메일은 필수입니다.");
        }
        return new SupplierInvitation(
            UUID.randomUUID(), tenantId, entityId,
            email.toLowerCase().strip(),
            UUID.randomUUID(),
            "PENDING", invitedBy,
            OffsetDateTime.now().plusDays(7), null, OffsetDateTime.now());
    }

    /** 초대 토큰 유효성 검증 (만료·상태 확인). */
    public void validateForActivation() {
        if (!"PENDING".equals(status)) {
            throw new EsgException(EsgErrorCode.INVITATION_EXPIRED,
                "이미 사용되었거나 만료된 초대입니다.");
        }
        if (OffsetDateTime.now().isAfter(expiresAt)) {
            throw new EsgException(EsgErrorCode.INVITATION_EXPIRED,
                "초대 링크가 만료되었습니다. 재초대를 요청하세요.");
        }
    }
}
