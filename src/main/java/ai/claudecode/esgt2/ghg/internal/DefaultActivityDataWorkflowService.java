package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultActivityDataWorkflowService implements ActivityDataWorkflowService {

    private final ActivityDataRepository activityDataRepository;

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_SUBMITTED")
    public ActivityDataResponse submitActivityData(UUID tenantId, UUID actorId, UUID activityDataId) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.submit(actorId);
        return toResponse(entity);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_APPROVED")
    public ActivityDataResponse approveActivityData(UUID tenantId, UUID actorId, UUID activityDataId) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.approve(actorId);
        return toResponse(entity);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_REJECTED")
    public ActivityDataResponse rejectActivityData(UUID tenantId, UUID actorId,
                                                    UUID activityDataId, String reason) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.reject(actorId, reason);
        return toResponse(entity);
    }

    private ActivityDataJpaEntity findOrThrow(UUID tenantId, UUID activityDataId) {
        return activityDataRepository.findByIdAndTenantId(activityDataId, tenantId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "활동 데이터를 찾을 수 없습니다: " + activityDataId));
    }

    private ActivityDataResponse toResponse(ActivityDataJpaEntity e) {
        return new ActivityDataResponse(
            e.getId(), e.getEntityId(),
            e.getReportingYear(), e.getCategory(), e.getSubCategory(),
            e.getQuantity(), e.getUnit(), e.getCountryCode(),
            e.getDataSource(), e.getDataQuality(), e.getStatus(),
            e.getCreatedAt());
    }
}
