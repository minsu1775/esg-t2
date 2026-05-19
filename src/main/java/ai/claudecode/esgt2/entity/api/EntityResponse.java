package ai.claudecode.esgt2.entity.api;

import ai.claudecode.esgt2.entity.domain.LegalEntityType;

import java.util.UUID;

public record EntityResponse(
    UUID id,
    String name,
    String countryCode,
    LegalEntityType entityType,
    boolean isActive
) {}
