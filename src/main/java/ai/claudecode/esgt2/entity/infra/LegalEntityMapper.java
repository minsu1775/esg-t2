package ai.claudecode.esgt2.entity.infra;

import ai.claudecode.esgt2.entity.domain.LegalEntity;

public final class LegalEntityMapper {

    private LegalEntityMapper() {}

    public static LegalEntityJpaEntity toEntity(LegalEntity domain) {
        return LegalEntityJpaEntity.builder()
            .tenantId(domain.tenantId())
            .name(domain.name())
            .countryCode(domain.countryCode())
            .entityType(domain.entityType())
            .build();
    }
}
