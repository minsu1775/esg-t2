package ai.claudecode.esgt2.entity.domain;

import java.util.UUID;

public record CreateLegalEntityCommand(
    UUID tenantId,
    String name,
    String countryCode,
    LegalEntityType entityType
) {}
