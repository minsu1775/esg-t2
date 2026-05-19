package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface GhgService {

    ActivityDataResponse createActivityData(UUID tenantId, UUID entityId, CreateActivityDataRequest request);

    List<ActivityDataResponse> findActivityData(UUID tenantId, UUID entityId, int reportingYear);

    List<EmissionRecordResponse> calculateEmissions(UUID tenantId, UUID entityId, int reportingYear);

    List<EmissionRecordResponse> findEmissionRecords(UUID tenantId, UUID entityId, int reportingYear);
}
