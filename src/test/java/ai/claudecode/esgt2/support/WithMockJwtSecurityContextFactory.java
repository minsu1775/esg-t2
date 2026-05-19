package ai.claudecode.esgt2.support;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;
import java.util.UUID;

public class WithMockJwtSecurityContextFactory implements WithSecurityContextFactory<WithMockJwtUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockJwtUser annotation) {
        var authentication = new JwtAuthentication(
            UUID.fromString(annotation.userId()),
            UUID.fromString(annotation.tenantId()),
            List.of(annotation.roles())
        );
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}
