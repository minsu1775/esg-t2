package ai.claudecode.esgt2.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwtUser {
    String userId() default "00000000-0000-0000-0000-000000000001";
    String tenantId() default "00000000-0000-0000-0000-000000000001";
    String[] roles() default {"ESG_VIEWER"};
}
