package ai.claudecode.esgt2.supply.internal;

import ai.claudecode.esgt2.entity.api.AuthService;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.supply.api.ActivateSupplierRequest;
import ai.claudecode.esgt2.supply.api.InviteSupplierRequest;
import ai.claudecode.esgt2.supply.api.SupplierDataRequest;
import ai.claudecode.esgt2.supply.api.SupplierInvitationResponse;
import ai.claudecode.esgt2.supply.api.SupplierService;
import ai.claudecode.esgt2.supply.domain.SupplierInvitation;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationJpaEntity;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultSupplierService implements SupplierService {

    private final SupplierInvitationRepository invitationRepository;
    private final AuthService authService;
    private final EntityManagementService entityManagementService;
    private final GhgService ghgService;
    private final ActivityDataWorkflowService workflowService;
    private final EmailGateway emailGateway;

    @Value("${supply.activation-base-url:http://localhost:3000/supply/activate}")
    private String activationBaseUrl;

    @Override
    @Transactional
    @Auditable(action = "SUPPLIER_INVITED")
    public SupplierInvitationResponse inviteSupplier(UUID tenantId, UUID actorId,
                                                      InviteSupplierRequest request) {
        // 법인 소속 검증
        entityManagementService.findById(tenantId, request.entityId())
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "법인을 찾을 수 없습니다: " + request.entityId()));

        var invitation = SupplierInvitation.create(
            tenantId, request.entityId(), request.email(), actorId);

        var entity = SupplierInvitationJpaEntity.builder()
            .id(invitation.id())
            .tenantId(invitation.tenantId())
            .entityId(invitation.entityId())
            .email(invitation.email())
            .token(invitation.token())
            .invitedBy(invitation.invitedBy())
            .expiresAt(invitation.expiresAt())
            .build();
        invitationRepository.save(entity);

        // 이메일 발송 (실패해도 초대는 저장됨)
        String activationLink = activationBaseUrl + "?token=" + invitation.token();
        emailGateway.sendInvitationEmail(invitation.email(), tenantId.toString(), activationLink);

        log.info("공급업체 초대 완료 (email={}, entity={})", invitation.email(), request.entityId());

        return new SupplierInvitationResponse(
            entity.getId(), entity.getEmail(), entity.getEntityId(),
            entity.getStatus(), entity.getExpiresAt());
    }

    @Override
    @Transactional
    public void activateAccount(ActivateSupplierRequest request) {
        var invEntity = invitationRepository.findByToken(request.token())
            .orElseThrow(() -> new EsgException(EsgErrorCode.INVITATION_NOT_FOUND,
                "유효하지 않은 초대 토큰입니다."));

        // 도메인 객체로 유효성 검증
        var invitation = toDomain(invEntity);
        invitation.validateForActivation();

        // 사용자 계정 생성
        authService.createSupplierUser(
            invEntity.getTenantId(), invEntity.getEmail(),
            request.password(), invEntity.getEntityId());

        // 초대 상태 ACCEPTED로 전이
        invEntity.accept();

        log.info("공급업체 계정 활성화 완료 (email={})", invEntity.getEmail());
    }

    @Override
    @Transactional
    @Auditable(action = "SUPPLIER_DATA_SUBMITTED")
    public ActivityDataResponse submitData(UUID tenantId, UUID actorId,
                                            UUID entityId, SupplierDataRequest request) {
        var ghgRequest = new CreateActivityDataRequest(
            request.reportingYear(), request.category(), request.subCategory(),
            request.quantity(), request.unit(), request.countryCode(),
            "SUPPLIER_PORTAL", null, null);
        return ghgService.createActivityData(tenantId, entityId, ghgRequest);
    }

    @Override
    @Transactional
    public ActivityDataResponse submitForReview(UUID tenantId, UUID actorId, UUID activityDataId) {
        return workflowService.submitActivityData(tenantId, actorId, activityDataId);
    }

    @Override
    @Transactional
    public ActivityDataResponse approveData(UUID tenantId, UUID actorId, UUID activityDataId) {
        return workflowService.approveActivityData(tenantId, actorId, activityDataId);
    }

    @Override
    @Transactional
    public ActivityDataResponse rejectData(UUID tenantId, UUID actorId,
                                            UUID activityDataId, String reason) {
        return workflowService.rejectActivityData(tenantId, actorId, activityDataId, reason);
    }

    private SupplierInvitation toDomain(SupplierInvitationJpaEntity e) {
        return new SupplierInvitation(
            e.getId(), e.getTenantId(), e.getEntityId(), e.getEmail(),
            e.getToken(), e.getStatus(), e.getInvitedBy(),
            e.getExpiresAt(), e.getAcceptedAt(), e.getCreatedAt());
    }
}
