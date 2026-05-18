---
name: domain-architecture
description: Domain≠Entity 분리, 서비스 네이밍, 불변성·재현성, 검증 우선 원칙
---

# 도메인 아키텍처 규칙

## Domain ≠ Entity 분리 (엄격 적용)

- **Domain 객체**: 순수 Java record/class. JPA·인프라 의존 없음.
- **JPA Entity**: 영속성 표현 전용. 비즈니스 로직 없음.
- Service는 **반드시 도메인 팩토리(`DomainObject.create(cmd)` 또는 `XxxMapper.toDomain()`)를 통해** 도메인 객체 생성.
- `JpaEntity.builder()...build()` 서비스에서 직접 호출 **금지** — 코드 리뷰에서 차단.
- 변환은 `XxxMapper` 정적 메서드로만 수행. Mapper 우회 변환 코드 금지.

```java
// ✅ 올바른 패턴
EmissionRecord domain = EmissionRecord.create(cmd);   // 도메인 팩토리
EmissionRecordJpaEntity entity = mapper.toEntity(domain);

// ❌ 금지
EmissionRecordJpaEntity entity = EmissionRecordJpaEntity.builder()...build();
```

## Service 인터페이스 — `Default*` 접두사

```java
public interface EmissionService { ... }

@Service
@RequiredArgsConstructor
public class DefaultEmissionService implements EmissionService { ... }
// ❌ EmissionServiceImpl 금지
```

## 불변성·재현성

- 산출에 사용된 데이터 수정 금지. 정정은 새 버전 INSERT.
- `DataPointVersion`, `CalculationResult`, `AuditLog` — INSERT only. DB 권한으로 `UPDATE/DELETE` 박탈.
- Snapshot에 FormulaVersion ID + EmissionFactor 버전 기록 → 재현성 보장.
- `Snapshot.state`: `ACTIVE → ARCHIVED`만 허용 (유일한 상태 변경).

## 검증 우선 원칙 (esg-t1 BUG-P3-04)

서비스 레이어에서 `create()` 호출 **이전에** 검증 완료.

```java
public DataPointId create(CreateDataPointCommand cmd) {
    validateCommand(cmd);               // 1. 먼저 검증
    DataPoint domain = DataPoint.create(cmd);  // 2. 그 후 생성
    ...
}
```

- `ERROR` severity → 저장 차단 (`ValidationException` throw).
- `WARNING` severity → 저장 허용 + 경고 플래그 세팅.

## Lombok 허용/금지

- **허용**: `@RequiredArgsConstructor`, `@Getter`, `@Builder`, `@Slf4j`
- **금지**: `@Data`, `@EqualsAndHashCode`, `@ToString`

## 로깅

`@Slf4j`만 사용. 개인정보·민감 데이터(HR 지표·증빙 파일명·공급사 거래 금액) 로그 마스킹.

## 승인 상태 기계

명시적 전이 메서드만 사용. `setStatus()` 직접 호출 금지.

```java
// 허용된 전이 메서드만 노출
entity.approve(actorId);
entity.reject(actorId, reason);   // reason 빈 문자열 불가
entity.escalate(actorId);
// entity.setStatus("APPROVED");  ← 금지
```

상태 전이: `PENDING → APPROVED | REJECTED | ESCALATED`, `ESCALATED → APPROVED | REJECTED`

## 컬럼명 SQL 예약어 금지

| 금지 | 대체 |
|---|---|
| `year` | `reporting_year` |
| `value` | `data_value` |
| `month` | `reporting_month` |
