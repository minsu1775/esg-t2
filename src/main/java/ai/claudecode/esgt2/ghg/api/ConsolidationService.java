package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.entity.api.ConsolidationMethod;

import java.util.List;
import java.util.UUID;

public interface ConsolidationService {

    ConsolidationResponse consolidate(UUID tenantId, UUID rootEntityId, int reportingYear, ConsolidationMethod method);

    List<ConsolidationResponse> findConsolidations(UUID tenantId, UUID rootEntityId, int reportingYear);
}
