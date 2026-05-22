package ai.claudecode.esgt2.supply.internal;

import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class ReminderService {

    private final SupplierInvitationRepository invitationRepository;
    private final EntityManagementService entityManagementService;
    private final GhgService ghgService;
    private final EmailGateway emailGateway;

    @Value("${supply.reminder.reporting-year:2025}")
    private int reportingYear;

    @Transactional(readOnly = true)
    public void sendPendingReminders() {
        // ACCEPTED 초대(계정 활성화 완료) 중 데이터 미제출 법인에 리마인더
        invitationRepository.findAll().stream()
            .filter(inv -> "ACCEPTED".equals(inv.getStatus()))
            .filter(inv -> !ghgService.hasActivityData(
                inv.getTenantId(), inv.getEntityId(), reportingYear))
            .forEach(inv -> {
                var entityOpt = entityManagementService.findById(
                    inv.getTenantId(), inv.getEntityId());
                if (entityOpt.isPresent()) {
                    emailGateway.sendReminderEmail(
                        inv.getEmail(),
                        entityOpt.get().name(),
                        reportingYear);
                    log.info("리마인더 발송 (email={}, entity={}, year={})",
                        inv.getEmail(), inv.getEntityId(), reportingYear);
                }
            });
    }
}
