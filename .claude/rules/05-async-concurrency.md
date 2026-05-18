---
name: async-concurrency
description: '@Async+@Transactional 분리, 트랜잭션 규칙, PESSIMISTIC_WRITE 락, Hash Chain 직렬화'
paths:
  - "src/main/java/**/service/**"
  - "src/main/java/**/audit/**"
  - "**/*Service.java"
  - "**/*Dispatcher.java"
  - "**/*Worker.java"
---

# 비동기·동시성 규칙

## @Async + @Transactional 분리 원칙 (esg-t1 Phase 8 교훈)

같은 메서드에 두 어노테이션 동시 부착 **금지**. 반드시 별도 빈으로 분리.

```java
// ❌ 금지
@Async
@Transactional
public void processAsync(Long id) { ... }

// ✅ 필수 — 두 빈으로 분리
@Service
public class AsyncDispatcher {
    private final TransactionalWorker worker;

    @Async
    public void dispatch(Long id) {
        worker.process(id);   // 별도 빈의 @Transactional 메서드 호출
    }
}

@Service
public class TransactionalWorker {
    @Transactional
    public void process(Long id) { ... }
}
```

이유: Spring 프록시 메커니즘상 두 AOP가 같은 메서드에 적용되면 하나가 무효화됨.

## 트랜잭션 규칙

- Service 메서드 단위 `@Transactional` 명시 (**클래스 레벨 금지**).
- 읽기 전용: `@Transactional(readOnly = true)`.
- Repository: `@Transactional` 부착 **금지**.
- 컨트롤러: `@Transactional` 부착 **금지**.

```java
@Service
public class DefaultEmissionService implements EmissionService {

    @Transactional(readOnly = true)
    public EmissionRecordResponse findById(UUID id) { ... }

    @Transactional
    @Auditable(action = "EMISSION_RECORD_CREATED")
    public EmissionRecordId create(CreateEmissionCommand cmd) { ... }
}
```

## CSV 대량 업로드 — Row-level 독립 트랜잭션 (esg-t1 Phase 3 교훈)

단일 트랜잭션으로 처리 시 하나의 오류가 전체 롤백 유발. 각 행을 `REQUIRES_NEW`로 독립 처리.

```java
@Service
public class CsvBulkImportService {
    private final ActivityDataRowImporter rowImporter;

    public BulkImportResult importCsv(List<CsvRow> rows) {
        return rows.stream()
            .map(rowImporter::importRow)   // 행별 독립 트랜잭션
            .collect(...);
    }
}

@Service
public class ActivityDataRowImporter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowResult importRow(CsvRow row) {
        try {
            // 단일 행 처리
            return ImportRowResult.success(row.lineNumber());
        } catch (Exception e) {
            return ImportRowResult.failure(row.lineNumber(), e.getMessage());
        }
    }
}
```

## Hash Chain — PESSIMISTIC_WRITE 락 (esg-t1 L-0-04)

`synchronized + @Transactional` 조합 **금지**. 트랜잭션 커밋 전 락 해제로 레이스 컨디션 발생.
반드시 DB 레벨 `PESSIMISTIC_WRITE` 락으로 트랜잭션 경계 내 순서 보장.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<AuditLogEntity> findLatestByTenantId(UUID tenantId);
```

## Hash Chain — Canonical JSON 직렬화 (esg-t1 L-0-08)

AuditLog 저장 경로와 검증 경로가 **반드시 동일한 직렬화 함수**를 호출해야 함.
필드 순서·null 처리가 달라지면 해시 불일치 → 항상 무결성 오류.

```java
// HashChainCalculator.java
public static Map<String, Object> canonicalPayload(AuditEvent event) {
    // 단일 정적 메서드로 저장·검증 경로 동일 직렬화 강제
    return new TreeMap<>(Map.of(
        "eventType", event.type(),
        "entityId", event.entityId().toString(),
        "actorId", event.actorId().toString(),
        "timestamp", event.timestamp().toEpochMilli()
        // null 필드 명시적 처리 필수
    ));
}
// 저장 시: canonicalPayload() → SHA-256
// 검증 시: canonicalPayload() → SHA-256 (동일 함수)
```

## DB Outbox 패턴

신뢰성 있는 모듈 간 비동기 이벤트는 `ApplicationEventPublisher` 직접 사용 금지.
트랜잭션 내 `outbox_events` INSERT → Outbox Poller → Publisher 경로 사용.

```java
// 이벤트 발행 (트랜잭션 내)
outboxRepository.save(OutboxEvent.of(tenantId, "EMISSION_CALCULATED", payload));

// Outbox Poller (별도 스케줄러)
@Scheduled(fixedDelay = 5000, zone = "Asia/Seoul")
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public void poll() {
    outboxRepository.findPending().forEach(event -> {
        publisher.publishEvent(event.toApplicationEvent());
        event.markProcessed();
    });
}
```

## Optimistic Lock 충돌 처리

`ObjectOptimisticLockingFailureException` → `GlobalExceptionHandler`에서 409 반환.
재시도 로직은 호출자(클라이언트)에게 위임. 서버 측 자동 재시도 금지.
