package ai.claudecode.esgt2.shared.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청마다 PostgreSQL 세션 변수를 설정해 RLS(Row-Level Security)가 올바른 테넌트 데이터만 반환하도록 한다.
 * SecurityFilter 이후, API 핸들러 이전에 실행된다.
 */
@Component
@RequiredArgsConstructor
public class TenantContextInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return true;
        }

        String tenantId = extractTenantId(auth);
        if (tenantId != null) {
            // set_config(setting, value, is_local=true): 현재 트랜잭션 범위로 격리, 파라미터 바인딩으로 SQL Injection 방어
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.current_tenant_id', ?, true)",
                String.class, tenantId);
        }
        return true;
    }

    private String extractTenantId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString("tenantId");
        }
        return null;
    }
}
