package ai.claudecode.esgt2.entity.api;

public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
