package ai.claudecode.esgt2.audit.internal;

import java.util.UUID;

record AuditIntegrityViolationEvent(UUID tenantId, Long auditLogId) {}
