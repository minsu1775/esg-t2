package ai.claudecode.esgt2.entity.api;

import java.util.UUID;

public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);

    /**
     * 공급업체 계정 생성 (SUPPLIER 역할 + entityId 스코프).
     * supply 모듈의 계정 활성화에서 호출.
     *
     * @return 생성된 사용자 ID
     */
    UUID createSupplierUser(UUID tenantId, String email, String password, UUID entityId);
}
