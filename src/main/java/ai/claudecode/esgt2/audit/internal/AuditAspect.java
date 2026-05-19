package ai.claudecode.esgt2.audit.internal;

import ai.claudecode.esgt2.audit.infra.OutboxEventJpaEntity;
import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
class AuditAspect {

    private final OutboxEventRepository outboxEventRepository;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Instant occurredAt = Instant.now();
        Object result = joinPoint.proceed();

        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthentication jwt) {
                UUID entityId = extractEntityId(result);
                String entityType = result != null ? result.getClass().getSimpleName() : null;

                outboxEventRepository.save(OutboxEventJpaEntity.builder()
                    .id(UUID.randomUUID())
                    .tenantId(jwt.getTenantId())
                    .eventType(auditable.action())
                    .actorId(jwt.getPrincipal())
                    .entityId(entityId)
                    .entityType(entityType)
                    .createdAt(occurredAt)
                    .build());
            }
        } catch (Exception e) {
            log.warn("AuditAspect: outbox 저장 실패 action={}", auditable.action(), e);
        }

        return result;
    }

    private UUID extractEntityId(Object result) {
        if (result == null) return null;
        try {
            var method = result.getClass().getMethod("id");
            var val = method.invoke(result);
            if (val instanceof UUID u) return u;
        } catch (Exception ignored) {}
        return null;
    }
}
