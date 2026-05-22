package ai.claudecode.esgt2.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            var jwt = jwtTokenProvider.decode(token);
            List<String> roles = jwt.getClaimAsStringList("roles");
            UUID userId = UUID.fromString(jwt.getSubject());
            String tenantIdStr = jwt.getClaimAsString("tenantId");
            UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
            String entityIdStr = jwt.getClaimAsString("entityId");
            UUID entityId = entityIdStr != null ? UUID.fromString(entityIdStr) : null;
            // VERIFIER 전용: 지정 스냅샷 ID
            String snapshotIdStr = jwt.getClaimAsString("verifier_snapshot_id");
            UUID snapshotId = snapshotIdStr != null ? UUID.fromString(snapshotIdStr) : null;

            var authentication = new JwtAuthentication(userId, tenantId, entityId, snapshotId,
                roles == null ? List.of() : roles);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.warn("JWT filter: unexpected error processing token", e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
