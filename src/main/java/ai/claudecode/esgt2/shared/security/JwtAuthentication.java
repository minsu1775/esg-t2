package ai.claudecode.esgt2.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tenantId;
    private final UUID entityId;   // SUPPLIER만 설정; 다른 역할은 null

    public JwtAuthentication(UUID userId, UUID tenantId, List<String> roles) {
        this(userId, tenantId, null, roles);
    }

    public JwtAuthentication(UUID userId, UUID tenantId, UUID entityId, List<String> roles) {
        super(roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.userId = userId;
        this.tenantId = tenantId;
        this.entityId = entityId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UUID getPrincipal() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    /** SUPPLIER 역할 사용자의 스코프 법인 ID. 다른 역할은 null. */
    public UUID getEntityId() {
        return entityId;
    }
}
