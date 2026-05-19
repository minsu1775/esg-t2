package ai.claudecode.esgt2.audit.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
class OutboxPollerScheduler {

    private final OutboxProcessingService processingService;

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        processingService.processNow();
    }
}
