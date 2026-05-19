package ai.claudecode.esgt2.ghg.infra;

import ai.claudecode.esgt2.ghg.domain.ActivityData;

public final class ActivityDataMapper {

    private ActivityDataMapper() {}

    public static ActivityDataJpaEntity toEntity(ActivityData domain) {
        return ActivityDataJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .reportingYear(domain.reportingYear())
            .category(domain.category())
            .subCategory(domain.subCategory())
            .quantity(domain.quantity())
            .unit(domain.unit())
            .countryCode(domain.countryCode())
            .dataSource(domain.dataSource())
            .dataQuality(domain.dataQuality())
            .build();
    }
}
