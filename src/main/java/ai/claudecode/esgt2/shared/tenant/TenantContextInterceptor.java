package ai.claudecode.esgt2.shared.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * 요청마다 PostgreSQL 세션 변수를 설정해 RLS(Row-Level Security)가 올바른 테넌트 데이터만 반환하도록 한다.
 * SecurityFilter 이후, API 핸들러 이전에 실행된다.
 */
@Component
@RequiredArgsConstructor
public class TenantContextInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;

    // JWT 없는 Webhook 경로: URL에서 tenantId를 추출해 RLS 컨텍스트 설정
    private static final Pattern WEBHOOK_TENANT_PATTERN =
        Pattern.compile(".*/api/v1/intake/tenants/([^/]+)/webhook$");

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        String tenantId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwtAuth && jwtAuth.getTenantId() != null) {
            tenantId = jwtAuth.getTenantId().toString();
        } else {
            // JWT 없는 경로(Webhook)에서도 RLS가 올바르게 동작하도록 URL에서 tenantId 추출
            tenantId = extractWebhookTenantId(request.getRequestURI());
        }

        if (tenantId != null) {
            // set_config(setting, value, is_local=true): 현재 트랜잭션 범위로 격리, 파라미터 바인딩으로 SQL Injection 방어
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.current_tenant_id', ?, true)",
                String.class, tenantId);
        }
        return true;
    }

    private String extractWebhookTenantId(String requestUri) {
        var matcher = WEBHOOK_TENANT_PATTERN.matcher(requestUri);
        if (matcher.matches()) {
            try {
                java.util.UUID.fromString(matcher.group(1));
                return matcher.group(1);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
