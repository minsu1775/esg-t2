package ai.claudecode.esgt2.vw.security;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * VwController {@code @PreAuthorize} SpEL 보안 빈.
 *
 * <pre>
 * {@code @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")}
 * </pre>
 *
 * <p>VERIFIER 역할이 자신에게 지정된 스냅샷 외의 스냅샷에 접근하려 하면 false 반환.
 * ESG_MANAGER 등 다른 역할은 {@code hasRole()} 체크로 별도 처리하므로 여기서 true 반환.
 */
@Component("snapshotSecurity")
public class SnapshotSecurityService {

    /**
     * 현재 인증 컨텍스트가 주어진 snapshotId에 접근 가능한지 검사.
     *
     * <ul>
     *   <li>VERIFIER 역할: JWT claim {@code verifier_snapshot_id}와 요청 snapshotId가 일치해야 허용</li>
     *   <li>그 외 역할: true 반환 (역할 기반 체크는 {@code hasRole()}로 별도 처리)</li>
     * </ul>
     */
    public boolean canAccess(UUID snapshotId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwt) {
            boolean isVerifier = jwt.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VERIFIER".equals(a.getAuthority()));
            if (isVerifier) {
                // VERIFIER: JWT claim의 snapshotId와 일치해야만 허용
                return snapshotId != null && snapshotId.equals(jwt.getSnapshotId());
            }
        }
        // 비-VERIFIER 역할: hasRole()이 주 게이트이므로 여기서 허용
        return true;
    }
}
