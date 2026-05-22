package ai.claudecode.esgt2.shared.event;

import java.util.UUID;

/**
 * 활동 데이터 정정 이벤트 — 배출량 재산출 트리거.
 * ghg 모듈에서 발행, ghg 모듈 내 ActivityDataEventHandler에서 수신.
 * shared/event 패키지 배치 (11-modulith-events.md: 이벤트는 shared에 위치).
 */
public record ActivityDataCorrectedEvent(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    UUID newActivityDataId
) {}
