package ai.claudecode.esgt2.audit.internal;

import ai.claudecode.esgt2.audit.domain.AuditEvent;
import ai.claudecode.esgt2.audit.domain.HashChainCalculator;
import ai.claudecode.esgt2.audit.infra.AuditLogJpaEntity;
import ai.claudecode.esgt2.audit.infra.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class AuditIntegrityService {

    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public void verifyAllChains() {
        List<UUID> tenants = auditLogRepository.findDistinctTenantIds();
        for (UUID tenantId : tenants) {
            verifyChain(tenantId);
        }
    }

    private void verifyChain(UUID tenantId) {
        List<AuditLogJpaEntity> logs = auditLogRepository.findByTenantIdOrderByIdAsc(tenantId);
        String prevHash = null;

        for (AuditLogJpaEntity entry : logs) {
            var event = new AuditEvent(
                entry.getTenantId(), entry.getEventType(), entry.getActorId(),
                entry.getEntityId(), entry.getEntityType(), entry.getOccurredAt()
            );
            String expected = HashChainCalculator.compute(event, prevHash);

            if (!expected.equals(entry.getCurrentHash())) {
                log.warn("Hash Chain 무결성 위반: tenantId={}, auditLogId={}", tenantId, entry.getId());
                publisher.publishEvent(new AuditIntegrityViolationEvent(tenantId, entry.getId()));
                return;
            }
            prevHash = entry.getCurrentHash();
        }
    }
}
