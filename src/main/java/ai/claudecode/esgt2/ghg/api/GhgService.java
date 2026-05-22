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

    /** 활동 데이터 정정 — 원본 ARCHIVED, 새 레코드 INSERT (P1: INSERT-only) (T-6B-03) */
    ActivityDataResponse correctActivityData(
        UUID tenantId, UUID actorId, UUID originalId, CorrectActivityDataRequest request);

    /** 버전 이력 조회 — 원본 포함 모든 정정 이력 (T-6B-05) */
    List<ActivityDataVersionResponse> findVersionHistory(UUID tenantId, UUID activityDataId);

    /** 정정 전·후 수치 비교 (T-6B-06) */
    ActivityDataDiffResponse findDiff(UUID tenantId, UUID correctedId);
}
