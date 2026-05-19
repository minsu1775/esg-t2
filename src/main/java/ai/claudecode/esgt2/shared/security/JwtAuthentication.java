package ai.claudecode.esgt2.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tenantId;

    public JwtAuthentication(UUID userId, UUID tenantId, List<String> roles) {
        super(roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.userId = userId;
        this.tenantId = tenantId;
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
}
