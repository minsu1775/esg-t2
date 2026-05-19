package ai.claudecode.esgt2.entity.api;

import java.util.List;
import java.util.UUID;

public interface EntityManagementService {
    EntityResponse create(UUID tenantId, CreateEntityRequest request);
    List<EntityResponse> findAll(UUID tenantId);
}
