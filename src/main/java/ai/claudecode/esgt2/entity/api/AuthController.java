package ai.claudecode.esgt2.entity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "이메일·비밀번호로 로그인 후 JWT 토큰을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "403", description = "인증 실패")
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새 Access Token을 발급합니다.")
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    @ApiResponse(responseCode = "403", description = "토큰 만료 또는 블랙리스트")
    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 블랙리스트에 등록합니다.")
    @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
