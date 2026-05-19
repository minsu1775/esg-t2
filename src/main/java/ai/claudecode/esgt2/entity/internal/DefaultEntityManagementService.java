package ai.claudecode.esgt2.entity.internal;

import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.api.EntityResponse;
import ai.claudecode.esgt2.entity.api.RelationshipResponse;
import ai.claudecode.esgt2.entity.api.SetRelationshipRequest;
import ai.claudecode.esgt2.entity.domain.CreateEntityRelationshipCommand;
import ai.claudecode.esgt2.entity.domain.CreateLegalEntityCommand;
import ai.claudecode.esgt2.entity.domain.EntityRelationship;
import ai.claudecode.esgt2.entity.domain.EntityRelationshipGraph;
import ai.claudecode.esgt2.entity.domain.LegalEntity;
import ai.claudecode.esgt2.entity.infra.EntityRelationshipJpaEntity;
import ai.claudecode.esgt2.entity.infra.EntityRelationshipRepository;
import ai.claudecode.esgt2.entity.infra.LegalEntityJpaEntity;
import ai.claudecode.esgt2.entity.infra.LegalEntityRepository;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultEntityManagementService implements EntityManagementService {

    private final LegalEntityRepository legalEntityRepository;
    private final EntityRelationshipRepository relationshipRepository;

    @Override
    @Transactional
    public EntityResponse create(UUID tenantId, CreateEntityRequest request) {
        var cmd = new CreateLegalEntityCommand(tenantId, request.name(),
            request.countryCode(), request.entityType());
        LegalEntity domain = LegalEntity.create(cmd);

        var jpaEntity = LegalEntityJpaEntity.builder()
            .tenantId(domain.tenantId())
            .name(domain.name())
            .countryCode(domain.countryCode())
            .entityType(domain.entityType())
            .build();

        var saved = legalEntityRepository.save(jpaEntity);
        return toEntityResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntityResponse> findAll(UUID tenantId) {
        return legalEntityRepository.findActiveByTenantId(tenantId).stream()
            .map(this::toEntityResponse)
            .toList();
    }

    @Override
    @Transactional
    public RelationshipResponse setRelationship(UUID tenantId, UUID parentId, SetRelationshipRequest request) {
        legalEntityRepository.findById(parentId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND, "부모 법인을 찾을 수 없습니다."));

        legalEntityRepository.findById(request.childId())
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND, "자식 법인을 찾을 수 없습니다."));

        var cmd = new CreateEntityRelationshipCommand(
            tenantId, parentId, request.childId(),
            request.ownershipRatio(), request.method(),
            request.effectiveFrom(), request.effectiveTo());
        EntityRelationship domain = EntityRelationship.create(cmd);

        var existing = relationshipRepository.findByTenantId(tenantId);
        var allRels = existing.stream()
            .map(e -> new CreateEntityRelationshipCommand(
                e.getTenantId(), e.getParentId(), e.getChildId(),
                e.getOwnershipRatio(), e.getMethod(),
                e.getEffectiveFrom(), e.getEffectiveTo()))
            .map(EntityRelationship::create)
            .toList();

        var newRelList = new java.util.ArrayList<>(allRels);
        newRelList.add(domain);
        EntityRelationshipGraph.of(newRelList);

        var jpaEntity = EntityRelationshipJpaEntity.builder()
            .tenantId(domain.tenantId())
            .parentId(domain.parentId())
            .childId(domain.childId())
            .ownershipRatio(domain.ownershipRatio())
            .method(domain.method())
            .effectiveFrom(domain.effectiveFrom())
            .effectiveTo(domain.effectiveTo())
            .build();

        var saved = relationshipRepository.save(jpaEntity);
        return toRelResponse(saved);
    }

    private EntityResponse toEntityResponse(LegalEntityJpaEntity e) {
        return new EntityResponse(e.getId(), e.getName(), e.getCountryCode(),
            e.getEntityType(), e.isActive());
    }

    private RelationshipResponse toRelResponse(EntityRelationshipJpaEntity r) {
        return new RelationshipResponse(r.getId(), r.getParentId(), r.getChildId(),
            r.getOwnershipRatio(), r.getMethod(), r.getEffectiveFrom(), r.getEffectiveTo());
    }
}
