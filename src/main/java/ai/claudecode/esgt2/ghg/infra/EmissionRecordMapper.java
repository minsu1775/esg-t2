package ai.claudecode.esgt2.ghg.infra;

import ai.claudecode.esgt2.ghg.domain.EmissionRecord;

public final class EmissionRecordMapper {

    private EmissionRecordMapper() {}

    public static EmissionRecordJpaEntity toEntity(EmissionRecord domain) {
        return EmissionRecordJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .activityDataId(domain.activityDataId())
            .reportingYear(domain.reportingYear())
            .scope(domain.scope())
            .ghgType(domain.ghgType())
            .emissionFactorId(domain.emissionFactorId())
            .rawEmission(domain.rawEmission())
            .build();
    }
}
