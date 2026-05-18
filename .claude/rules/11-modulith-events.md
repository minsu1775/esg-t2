---
name: modulith-events
description: Spring Modulith 모듈 경계, ApplicationEventPublisher, @Auditable AOP 적용 범위
paths:
  - "src/main/java/**/module/**"
  - "src/main/java/**/shared/**"
  - "**/*Event.java"
  - "**/*EventHandler*.java"
  - "**/*ApplicationService*.java"
---

# Spring Modulith & 이벤트 규칙

## 모듈 경계 (ModularityTest 필수)

```
ai.claudecode.esgt2
├── ghg/        # GHG 배출량 계산
│   ├── api/    # 다른 모듈에서 호출 가능한 공개 인터페이스
│   ├── domain/ # 도메인 객체 (순수 Java)
│   ├── infra/  # JPA Repository, 외부 연동
│   └── internal/ # 모듈 내부 전용 (외부 접근 금지)
├── entity/     # 법인·테넌트 관리
├── audit/      # AuditLog, Hash Chain
├── vw/         # Verification Workspace
├── rpt/        # 보고서 생성
├── supply/     # 공급업체 포털
└── shared/     # 공통 Value Object, Event, Exception
```

`./gradlew test --tests "*ModularityTest"` — Phase 0부터 CI에서 상시 통과 유지.

## 모듈 간 통신 규칙

| 통신 유형 | 허용 방법 | 금지 |
|---|---|---|
| 동기 호출 | `api/` 패키지 공개 인터페이스 | `internal/` 직접 참조 |
| 비동기 통신 | `ApplicationEventPublisher` + `@ApplicationModuleListener` | 직접 Repository 크로스 참조 |

```java
// ✅ 모듈 간 비동기 이벤트
// 발행 (ghg 모듈)
publisher.publishEvent(new EmissionCalculatedEvent(tenantId, entityId, reportingYear));

// 수신 (rpt 모듈)
@ApplicationModuleListener
public void onEmissionCalculated(EmissionCalculatedEvent event) {
    reportService.triggerRegeneration(event.entityId());
}
```

## @Auditable AOP — 데이터 변경 메서드 전수 적용

create / update / delete / approve / reject / escalate / correct 계열 메서드에 **반드시** 부착.

```java
@Auditable(action = "ACTIVITY_DATA_CREATED")
public ActivityDataId create(CreateActivityDataCommand cmd) { ... }

@Auditable(action = "ACTIVITY_DATA_CORRECTED")
public ActivityDataId correct(CorrectActivityDataCommand cmd) { ... }

@Auditable(action = "REPORT_APPROVED")
public void approve(UUID reportId, UUID approverId) { ... }
```

**코드 리뷰 체크**: 데이터 변경 메서드에 `@Auditable` 없으면 차단.

## 이벤트 설계 원칙

- 이벤트 클래스는 `shared/` 패키지에 위치 (모듈 간 공유).
- 이벤트는 불변 record로 선언.
- 이벤트에 도메인 객체 전체 포함 금지 — ID만 포함 후 수신 측에서 조회.

```java
// shared/event/
public record EmissionCalculatedEvent(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String scope
) {}
```

## DB Outbox — 신뢰성 있는 이벤트 전달

`ApplicationEventPublisher` 직접 사용 시 트랜잭션 롤백 후에도 이벤트가 발행될 수 있음.
신뢰성이 필요한 이벤트는 DB Outbox 패턴 사용.

```java
@Transactional
public void create(CreateCmd cmd) {
    // ... 비즈니스 로직
    // Outbox에 이벤트 저장 (트랜잭션 내, 롤백 시 함께 롤백)
    outboxRepository.save(OutboxEvent.of(tenantId, "EMISSION_CALCULATED", payload));
}
// Outbox Poller가 후속으로 ApplicationEventPublisher.publishEvent() 호출
```

## 모듈별 ModularityTest 통과 기준

각 Phase 완료 기준(DoD)에 해당 모듈 `ModularityTest` 통과 포함.
PR 생성 전 `./gradlew test --tests "*ModularityTest"` 로컬 실행 확인 필수.
