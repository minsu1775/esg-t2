package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.shared.event.ActivityDataCorrectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활동 데이터 정정 이벤트 핸들러 — 배출량 재산출 트리거.
 * <p>
 * 동일 모듈(ghg) 내 이벤트이므로 {@code @EventListener} 사용
 * (모듈 간 이벤트는 {@code @ApplicationModuleListener}).
 * <p>
 * T-6B-04: correctActivityData 완료 후 자동으로 배출량 재산출.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ActivityDataEventHandler {

    private final DefaultGhgService ghgService;

    /**
     * 활동 데이터 정정 이벤트 수신 — 해당 연도 배출량 재산출.
     * <p>
     * append-only 원칙: 기존 EmissionRecord는 삭제하지 않고
     * 새 레코드를 INSERT 한다. 조회 시 ARCHIVED 활동 데이터를
     * 기준으로 필터링하여 정정 후 최신 레코드만 사용한다.
     */
    @EventListener
    @Transactional
    public void onActivityDataCorrected(ActivityDataCorrectedEvent event) {
        log.info("[T-6B-04] 배출량 재산출 트리거 — tenantId={}, entityId={}, year={}",
            event.tenantId(), event.entityId(), event.reportingYear());
        ghgService.calculateEmissions(event.tenantId(), event.entityId(), event.reportingYear());
    }
}
