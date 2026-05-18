---
name: scheduler
description: 'cron 스케줄러 zone=Asia/Seoul 필수 (fixedDelay/fixedRate 불가), @ConditionalOnProperty 테스트 격리, Outbox Poller 규칙'
paths:
  - "**/*Scheduler*.java"
  - "**/*Job*.java"
  - "**/*Poller*.java"
  - "**/*BatchRunner*.java"
  - "src/main/resources/application*.yml"
---

# 스케줄러 규칙

## zone = "Asia/Seoul" 필수 (esg-t1 BUG-P4-12)

`cron` 기반 `@Scheduled`에 시간대 미지정 시 JVM 기본 시간대(UTC)로 동작 → KST와 9시간 차이 발생.
**`cron` 속성 사용 시 반드시 `zone = "Asia/Seoul"` 명시.**

> ⚠️ `zone` 속성은 `cron` 에서만 유효. `fixedDelay`/`fixedRate`에 `zone` 사용 시 컴파일 오류 또는 무시. fixedDelay/fixedRate는 실행 간격이 절대 시각이 아닌 상대 시간이므로 시간대 설정 불필요.

```java
// ❌ 금지 — 시간대 미지정
@Scheduled(cron = "0 0 2 * * *")
public void runAt2AM() { ... }

// ✅ 필수
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
public void runAt2AM() { ... }
```

## @ConditionalOnProperty 테스트 격리 (esg-t1 BUG-P4-04)

통합 테스트 중 스케줄러 자동 실행으로 테스트 데이터 오염 방지.
**모든 스케줄러 빈에 `@ConditionalOnProperty` 필수.**

```java
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class EmissionFactorSyncScheduler {

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void syncEmissionFactors() {
        log.info("배출계수 동기화 시작");
        // ...
    }
}
```

```yaml
# application-test.yml
scheduler:
  enabled: false   # 통합 테스트에서 스케줄러 비활성화

# application-prod.yml
scheduler:
  enabled: true
```

## Outbox Poller 규칙

```java
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class OutboxPoller {

    @Scheduled(fixedDelay = 5000)   // fixedDelay는 zone 속성 미지원
    @Transactional
    public void poll() {
        outboxRepository.findPending(BATCH_SIZE).forEach(event -> {
            try {
                publisher.publishEvent(event.toApplicationEvent());
                event.markProcessed();
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                log.error("Outbox 이벤트 처리 실패: {}", event.getId(), e);
            }
        });
    }
}
```

- 실패한 이벤트: `status = FAILED` + 에러 메시지 기록, 재시도 횟수 제한.
- `PROCESSED` 상태로 업데이트 후 재발행 없음 (중복 처리 방지).

## Hash Chain 무결성 검증 스케줄러

```java
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class HashChainVerificationScheduler {

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")   // 매일 새벽 2시 KST
    public void verifyHashChainIntegrity() {
        log.info("Hash Chain 무결성 검증 시작");
        // 전체 audit_logs 순차 조회 → 해시 재계산 → 불일치 시 알림 이벤트 발행
    }
}
```

## 스케줄러와 트랜잭션

스케줄러 메서드 자체에 `@Transactional` 부착 금지.
트랜잭션이 필요한 작업은 별도 Service 빈의 `@Transactional` 메서드를 호출.

```java
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
public void sync() {
    syncService.doSync();   // @Transactional은 syncService에
}
```
