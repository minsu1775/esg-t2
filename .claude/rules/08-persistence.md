---
name: persistence
description: Flyway 멀티 로케이션, Append-only Repository, JPA N+1 방지, Spring Modulith 경계
paths:
  - "src/main/java/**/infra/**"
  - "src/main/java/**/repository/**"
  - "src/main/resources/db/**"
  - "**/*Repository*.java"
  - "**/*JpaEntity*.java"
  - "**/*Entity.java"
  - "**Migration*.sql"
---

# 영속성 계층 규칙

## Flyway 멀티 로케이션 전략 (esg-t1 BUG-P5-09)

| 위치 | 용도 |
|---|---|
| `db/migration` | H2 · PostgreSQL 공통 DDL |
| `db/migration-pg` | PostgreSQL 전용 DDL (RLS 정책, 파티션, PG 함수) |

```yaml
# application-prod.yml — 운영 환경: 두 위치 모두
spring:
  flyway:
    locations:
      - classpath:db/migration
      - classpath:db/migration-pg

# application-test.yml — 테스트: 공통만 (H2 호환)
spring:
  flyway:
    locations:
      - classpath:db/migration
```

PG 전용 DDL을 공통 위치에 넣으면 H2 테스트에서 파싱 오류 → **반드시 분리**.

## Append-only Repository 패턴 (esg-t1 BUG-P3-07)

불변 엔티티는 `JpaRepository` 대신 **`Repository<T, ID>` 마커 인터페이스** 상속.
`delete*`, `deleteAll*` 메서드가 컴파일 타임에 노출되지 않음.

적용 대상: `AuditLog`, `DataPointVersion`, `CalculationResult`, `VerificationSnapshot`

```java
// ✅ 불변 엔티티 Repository
public interface AuditLogRepository extends Repository<AuditLogEntity, UUID> {
    AuditLogEntity save(AuditLogEntity entity);
    Optional<AuditLogEntity> findById(UUID id);
    // delete 메서드 의도적 미노출
}

// ❌ 금지 — JpaRepository 상속 시 delete 노출됨
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> { ... }
```

## JPA 연관 관계 로딩 정책

- 기본값: **`LAZY`** — 모든 `@ManyToOne`, `@OneToMany`에 `fetch = FetchType.LAZY` 명시.
- 조회 서비스: JPQL `JOIN FETCH` 또는 `@EntityGraph`로 명시적 fetch.

```java
// ✅ N+1 방지
@Query("SELECT e FROM EmissionRecordEntity e JOIN FETCH e.activityData WHERE e.tenantId = :tenantId")
List<EmissionRecordEntity> findAllWithActivityData(@Param("tenantId") UUID tenantId);
```

- `@EAGER` 절대 금지 (전체 연관 테이블 즉시 로드 → 성능 문제).

## Spring Modulith 모듈 경계

- 다른 모듈의 `internal` 패키지 직접 참조 **금지**.
- 모듈 간 동기 호출: `api/` 패키지 공개 인터페이스만 허용.
- 모듈 간 비동기 통신: `ApplicationEventPublisher` → `@ApplicationModuleListener`.
- 직접 Repository 크로스 참조 금지 (`ModularityTest`에서 빌드 타임 강제).

```java
// ❌ 금지 — 다른 모듈 internal 직접 접근
import ai.claudecode.esgt2.audit.internal.AuditAspect;

// ✅ 허용 — 다른 모듈 공개 API
import ai.claudecode.esgt2.audit.api.AuditService;
```

## @Transactional 위치 규칙

- Service 메서드 단위에만 선언 (클래스 레벨 금지).
- Repository: `@Transactional` 부착 **금지**.
- 읽기 전용: `@Transactional(readOnly = true)`.

## DB 스키마 컬럼 규칙

- PK: `UUID PRIMARY KEY DEFAULT gen_random_uuid()`
- Audit 컬럼: `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ`
- 수치 컬럼: `NUMERIC(20, 6)` — float/double 타입 컬럼 금지
- SQL 예약어 컬럼명 금지: `year` → `reporting_year`, `value` → `data_value`

## INSERT-only 테이블 DB 권한

`AuditLog`, `CalculationResult`, `VerificationSnapshot` 테이블에 `UPDATE/DELETE` DB 권한 박탈:

```sql
REVOKE UPDATE, DELETE ON audit_logs FROM app_user;
REVOKE UPDATE, DELETE ON calculation_results FROM app_user;
REVOKE UPDATE, DELETE ON verification_snapshots FROM app_user;
```
