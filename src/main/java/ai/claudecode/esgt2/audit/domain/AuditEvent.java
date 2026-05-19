package ai.claudecode.esgt2.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID tenantId,
    String eventType,
    UUID actorId,
    UUID entityId,
    String entityType,
    Instant occurredAt
) {}
