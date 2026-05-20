package ai.claudecode.esgt2.entity.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityManagementService {
    EntityResponse create(UUID tenantId, CreateEntityRequest request);
    List<EntityResponse> findAll(UUID tenantId);
    Optional<EntityResponse> findById(UUID tenantId, UUID entityId);
    RelationshipResponse setRelationship(UUID tenantId, UUID parentId, SetRelationshipRequest request);
    List<RelationshipResponse> findRelationships(UUID tenantId);
}
