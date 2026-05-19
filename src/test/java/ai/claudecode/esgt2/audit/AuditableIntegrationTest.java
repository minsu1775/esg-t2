package ai.claudecode.esgt2.audit;

import ai.claudecode.esgt2.audit.infra.AuditLogRepository;
import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.audit.internal.OutboxProcessingService;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditableIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagementService entityManagementService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxProcessingService outboxProcessingService;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setupAuth() {
        var auth = new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("TENANT_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    @Transactional
    void cleanup() {
        auditLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    void Auditable_메서드_실행_후_outbox_이벤트가_생성된다() {
        var request = new CreateEntityRequest("테스트법인", "KR", LegalEntityType.PARENT);
        entityManagementService.create(TENANT_ID, request);

        assertThat(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING")).hasSize(1);
    }

    @Test
    void outbox_처리_후_AuditLog가_생성된다() {
        var request = new CreateEntityRequest("감사법인", "KR", LegalEntityType.PARENT);
        entityManagementService.create(TENANT_ID, request);

        outboxProcessingService.processNow();

        var logs = auditLogRepository.findByTenantIdOrderByIdAsc(TENANT_ID);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("ENTITY_CREATED");
        assertThat(logs.get(0).getActorId()).isEqualTo(ACTOR_ID);
        assertThat(logs.get(0).getCurrentHash()).isNotBlank();
    }

    @Test
    void 연속_두_이벤트_처리_후_Hash_Chain이_연결된다() {
        var req1 = new CreateEntityRequest("법인1", "KR", LegalEntityType.PARENT);
        var req2 = new CreateEntityRequest("법인2", "JP", LegalEntityType.SUBSIDIARY);
        entityManagementService.create(TENANT_ID, req1);
        entityManagementService.create(TENANT_ID, req2);

        outboxProcessingService.processNow();

        var logs = auditLogRepository.findByTenantIdOrderByIdAsc(TENANT_ID);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getPreviousHash()).isNull();
        assertThat(logs.get(1).getPreviousHash()).isEqualTo(logs.get(0).getCurrentHash());
    }

    @Test
    void Auditable_없는_메서드_실행_시_outbox_이벤트가_생성되지_않는다() {
        entityManagementService.findAll(TENANT_ID);

        assertThat(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING")).isEmpty();
    }
}
