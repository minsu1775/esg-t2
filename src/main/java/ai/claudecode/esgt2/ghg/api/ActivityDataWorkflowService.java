package ai.claudecode.esgt2.ghg.api;

import java.util.UUID;

/** activity_data 상태 전이 공개 API (supply 모듈에서 호출 가능). */
public interface ActivityDataWorkflowService {

    /**
     * DRAFT → PENDING (공급업체 제출).
     */
    ActivityDataResponse submitActivityData(UUID tenantId, UUID actorId, UUID activityDataId);

    /**
     * PENDING → APPROVED (ESG_MANAGER 승인).
     */
    ActivityDataResponse approveActivityData(UUID tenantId, UUID actorId, UUID activityDataId);

    /**
     * PENDING → REJECTED (ESG_MANAGER 반려).
     *
     * @param reason 반려 사유 (빈 문자열 불가)
     */
    ActivityDataResponse rejectActivityData(UUID tenantId, UUID actorId, UUID activityDataId,
                                             String reason);
}
