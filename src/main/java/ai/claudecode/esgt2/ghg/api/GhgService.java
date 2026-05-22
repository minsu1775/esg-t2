package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface GhgService {

    ActivityDataResponse createActivityData(UUID tenantId, UUID entityId, CreateActivityDataRequest request);

    List<ActivityDataResponse> findActivityData(UUID tenantId, UUID entityId, int reportingYear);

    List<EmissionRecordResponse> calculateEmissions(UUID tenantId, UUID entityId, int reportingYear);

    List<EmissionRecordResponse> findEmissionRecords(UUID tenantId, UUID entityId, int reportingYear);

    /** 지정 법인이 해당 연도에 활동 데이터를 하나라도 보유하는지 확인. 리마인더 스케줄러 사용. */
    boolean hasActivityData(UUID tenantId, UUID entityId, int reportingYear);
}
