package ai.claudecode.esgt2.audit.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private UUID actorId;

    private UUID entityId;
    private String entityType;

    private String previousHash;

    @Column(nullable = false)
    private String currentHash;

    @Column(nullable = false)
    private Instant occurredAt;

    @Builder
    public AuditLogJpaEntity(UUID tenantId, String eventType, UUID actorId,
                              UUID entityId, String entityType,
                              String previousHash, String currentHash,
                              Instant occurredAt) {
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.entityId = entityId;
        this.entityType = entityType;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.occurredAt = occurredAt;
    }
}
