package ai.claudecode.esgt2.audit.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private UUID actorId;

    private UUID entityId;
    private String entityType;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    @Builder
    public OutboxEventJpaEntity(UUID id, UUID tenantId, String eventType,
                                 UUID actorId, UUID entityId, String entityType,
                                 Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.entityId = entityId;
        this.entityType = entityType;
        this.status = "PENDING";
        this.createdAt = createdAt;
    }

    public void markProcessed() {
        this.status = "PROCESSED";
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.status = "FAILED";
    }
}
