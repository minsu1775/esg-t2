package ai.claudecode.esgt2.entity.internal;

import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.api.EntityResponse;
import ai.claudecode.esgt2.entity.domain.CreateLegalEntityCommand;
import ai.claudecode.esgt2.entity.domain.LegalEntity;
import ai.claudecode.esgt2.entity.infra.LegalEntityJpaEntity;
import ai.claudecode.esgt2.entity.infra.LegalEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultEntityManagementService implements EntityManagementService {

    private final LegalEntityRepository legalEntityRepository;

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
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntityResponse> findAll(UUID tenantId) {
        return legalEntityRepository.findActiveByTenantId(tenantId).stream()
            .map(this::toResponse)
            .toList();
    }

    private EntityResponse toResponse(LegalEntityJpaEntity e) {
        return new EntityResponse(e.getId(), e.getName(), e.getCountryCode(),
            e.getEntityType(), e.isActive());
    }
}
