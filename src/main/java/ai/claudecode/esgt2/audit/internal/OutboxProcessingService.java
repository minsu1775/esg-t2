package ai.claudecode.esgt2.audit.internal;

import ai.claudecode.esgt2.audit.domain.AuditEvent;
import ai.claudecode.esgt2.audit.domain.HashChainCalculator;
import ai.claudecode.esgt2.audit.infra.AuditLogJpaEntity;
import ai.claudecode.esgt2.audit.infra.AuditLogRepository;
import ai.claudecode.esgt2.audit.infra.OutboxEventJpaEntity;
import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessingService {

    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void processNow() {
        List<OutboxEventJpaEntity> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (OutboxEventJpaEntity event : pending) {
            try {
                appendAuditLog(event);
                event.markProcessed();
            } catch (Exception e) {
                log.warn("Outbox processing failed for event id={}", event.getId(), e);
                event.markFailed();
            }
            outboxEventRepository.save(event);
        }
    }

    private void appendAuditLog(OutboxEventJpaEntity event) {
        var latest = auditLogRepository.findFirstByTenantIdOrderByIdDesc(event.getTenantId());
        String prevHash = latest.map(AuditLogJpaEntity::getCurrentHash).orElse(null);

        var auditEvent = new AuditEvent(
            event.getTenantId(),
            event.getEventType(),
            event.getActorId(),
            event.getEntityId(),
            event.getEntityType(),
            event.getCreatedAt()
        );

        String currentHash = HashChainCalculator.compute(auditEvent, prevHash);

        auditLogRepository.save(AuditLogJpaEntity.builder()
            .tenantId(auditEvent.tenantId())
            .eventType(auditEvent.eventType())
            .actorId(auditEvent.actorId())
            .entityId(auditEvent.entityId())
            .entityType(auditEvent.entityType())
            .previousHash(prevHash)
            .currentHash(currentHash)
            .occurredAt(auditEvent.occurredAt())
            .build());
    }
}
