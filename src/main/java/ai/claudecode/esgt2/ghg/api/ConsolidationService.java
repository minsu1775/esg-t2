package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface ConsolidationService {

    ConsolidationResponse consolidate(UUID tenantId, UUID rootEntityId, int reportingYear, String method);

    List<ConsolidationResponse> findConsolidations(UUID tenantId, UUID rootEntityId, int reportingYear);
}
