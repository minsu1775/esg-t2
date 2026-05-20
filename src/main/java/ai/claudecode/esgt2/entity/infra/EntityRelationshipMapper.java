package ai.claudecode.esgt2.entity.infra;

import ai.claudecode.esgt2.entity.domain.EntityRelationship;

public final class EntityRelationshipMapper {

    private EntityRelationshipMapper() {}

    public static EntityRelationshipJpaEntity toEntity(EntityRelationship domain) {
        return EntityRelationshipJpaEntity.builder()
            .tenantId(domain.tenantId())
            .parentId(domain.parentId())
            .childId(domain.childId())
            .ownershipRatio(domain.ownershipRatio())
            .method(domain.method())
            .effectiveFrom(domain.effectiveFrom())
            .effectiveTo(domain.effectiveTo())
            .build();
    }
}
