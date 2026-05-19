package ai.claudecode.esgt2.audit.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class AuditIntegrityScheduler {

    private final AuditIntegrityService integrityService;

    // @Transactional 금지: 스케줄러 메서드에 직접 부착 불가 (09-scheduler.md)
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void verifyAllChains() {
        log.info("Hash Chain 무결성 검증 시작");
        integrityService.verifyAllChains();
    }
}
