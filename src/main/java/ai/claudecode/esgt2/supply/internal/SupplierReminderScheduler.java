package ai.claudecode.esgt2.supply.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미제출 법인 자동 리마인더 스케줄러 (T-6-11).
 * 매주 월요일 09:00 KST 실행.
 *
 * <p>스케줄러 @Transactional 금지 원칙(05-async-concurrency.md):
 * 트랜잭션 작업은 {@link ReminderService}에 위임.
 */
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class SupplierReminderScheduler {

    private final ReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void sendReminders() {
        log.info("공급업체 미제출 리마인더 스케줄 시작");
        reminderService.sendPendingReminders();
    }
}
