# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **선행 프로젝트**: `../esg-t1` — MVP Phase 0~12 완성본. esg-t2는 esg-t1의 아키텍처 교훈과 규제 업데이트(KSSB 1/2, 2026-02-26)를 반영한 2세대 프로젝트다.
> **문서 읽는 순서**: `docs/regulatory.md` → `docs/prd.md` → `docs/spec.md` → `docs/plan.md` → `docs/task.md` → 본 파일

---

## 프로젝트 개요

ESG(환경·사회·지배구조) 공시지원 시스템 2세대. KSSB 1/2(한국판 IFRS S1/S2), Scope 3, 다법인 연결, 외부 검증 모드를 MVP에 포함한 엔터프라이즈 공시 플랫폼.

---

## 프로젝트 좌표

- **Group**: `ai.claudecode`
- **Artifact / Name**: `esgt2`
- **Base package**: `ai.claudecode.esgt2`
- **초기 생성**: 반드시 **Spring Initializr** (https://start.spring.io) 사용. 수동 `build.gradle.kts` 작성 금지.

---

## 기술 스택 (확정)

### Backend
- **Spring Boot 4.0.x (latest GA)** — Spring Framework 7 기반
- **Java 25 LTS** — Virtual Threads (Project Loom), Record Patterns 활용
- **Spring Modulith** — 모듈 경계 빌드 타임 강제
- **JPA (Jakarta Persistence 3.2) + Hibernate 7**
- **PostgreSQL 18** — 운영·통합 테스트 (TestContainers)
- **H2** — 로컬 개발 보조
- **Lombok 1.18.34+**
- **Gradle (Kotlin DSL)**
- **springdoc-openapi** — OpenAPI 3.1 자동 생성
- **OpenTelemetry (Spring Boot 4 내장)** — OTLP 메트릭·트레이스 내보내기

### Frontend
- **Next.js (latest)** — App Router + TypeScript
- **UI 개발 게이트**: UI Phase 시작 전 반드시 사용자에게 알리고, 디자인 시스템 확인 후 명시적 승인을 받을 때까지 UI 코드 작성 금지.

---

## 명령어

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스
./gradlew test --tests "ai.claudecode.esgt2.domain.DataPointTest"

# 단일 테스트 메서드
./gradlew test --tests "ai.claudecode.esgt2.domain.DataPointTest.정정시_새_버전이_추가된다"

# Spring Modulith 모듈 검증
./gradlew test --tests "*ModularityTest"

# OpenAPI 스펙 추출
./gradlew generateOpenApiDocs

# Spotless 포맷
./gradlew spotlessApply

# 프론트엔드
cd frontend && npm run dev
cd frontend && npm run typecheck
cd frontend && npm run build
```

---

## 아키텍처 개요

상세는 `docs/spec.md` 참조.

```
ai.claudecode.esgt2
├── domain          # 순수 도메인 (JPA 의존 없음)
├── module
│   ├── dm          # Data Module — 수집·정제·버전·공급망 데이터
│   ├── ce          # Calculation Engine — Scope 1·2·3, 다법인 연결
│   ├── re          # Rule/Metadata Engine — KSSB·GRI·SASB 산식·매핑
│   ├── rg          # Report Generator — Word/PDF/iXBRL(설계 준비)
│   ├── wf          # Workflow Engine — 다단계 승인
│   ├── at          # Audit Trail — @Auditable AOP + 해시 체인
│   ├── vw          # Verification Workspace — 외부 검증인 전용
│   ├── ig          # Integration Gateway — ERP/EMS/SCM 어댑터
│   ├── se          # Security/Identity — Keycloak + RBAC+ABAC
│   └── op          # Operations — 변경·릴리스·인시던트·백업·모니터링
├── common          # 공통 예외·DTO·유틸
└── config          # Spring Config
```

**Spring Modulith**: `@ApplicationModule` 선언 + `ModularityTest`로 모듈 간 의존성을 빌드 타임에 검증.

---

## 코딩 관례 (반드시 준수)

### 1. Domain ≠ Entity 분리 (엄격 적용)

- **Domain 객체**: 순수 Java record/class. 인프라 의존 없음.
- **JPA Entity**: 영속성 표현 전용.
- Service는 **반드시 도메인 팩토리(or `XxxMapper.toDomain()`)를 통해** 도메인 객체를 생성. `Entity.builder().build()` 직접 호출 금지.
- 변환은 `XxxMapper` 정적 메서드로 분리. Mapper를 우회하는 변환 코드는 코드 리뷰에서 차단.

### 2. Service 인터페이스 — `Default*` 접두사

```java
// 인터페이스
public interface DataPointService { ... }

// 구현 (Impl 접미사 금지)
@Service
@RequiredArgsConstructor
public class DefaultDataPointService implements DataPointService { ... }
```

### 3. @Auditable AOP — AuditTrail 누락 방지

데이터 변경 메서드에는 반드시 `@Auditable(action = "...")` 어노테이션을 붙인다.
AOP Aspect가 없는 `@Auditable` 호출을 감지하고 테스트에서 검증한다.

```java
@Auditable(action = "DATA_POINT_CREATED")
public DataPointId create(CreateDataPointCommand cmd) { ... }
```

**코드 리뷰 체크**: 데이터 변경 메서드에 `@Auditable` 없으면 차단.

### 4. OpenAPI

모든 REST API에 `@Operation`, `@ApiResponse`, `@Schema` 적용.
빌드 시 `docs/openapi.yml` 자동 생성·갱신, git에 커밋.

### 5. 불변성·재현성

- 산출에 사용된 데이터 수정 금지. 정정은 새 버전 INSERT.
- `DataPointVersion`, `CalculationResult`, `AuditLog` — INSERT only. DB 권한 차원에서 `UPDATE/DELETE` 박탈.
- Snapshot에 FormulaVersion ID + EmissionFactor 버전 기록 → 재현성 보장.

### 6. 산식 평가기 보안 + DoS 방어 (esg-t1 BUG-P5-03)

커스텀 재귀 하강 파서 (`Lexer → Parser → CustomExpressionEvaluator`).
화이트리스트 함수(`+ - * / abs min max sum if pow log`)만 허용.
`eval`-style 임의 코드 실행, SpEL 직접 사용, Reflection 절대 금지.

**DoS 방어 한계값 (반드시 상수로 선언):**

```java
// FormulaConstants.java
public static final int MAX_EXPRESSION_LENGTH = 1000;   // 수식 전체 길이
public static final int MAX_NUMBER_LENGTH     = 50;     // 숫자 리터럴 자릿수
public static final int MAX_PARSER_DEPTH      = 50;     // Parser 재귀 깊이
public static final int MAX_EVAL_DEPTH        = 50;     // Evaluator 재귀 깊이
```

- 한계값 초과 시 `FormulaValidationException`(400) 반환. 서버 스택 오버플로 없음.
- YAML 로더 시 `test_cases` 비어 있으면 퍼블리시 거부.

### 7. YAML 로더 멱등성 (esg-t1 BUG-P5-07 교훈)

YAML 로더는 **항목 단위** 존재 여부 확인 후 없는 것만 INSERT.
파일 단위 skip 금지. `DataIntegrityViolationException` 발생 시 warn 로그 + 계속 진행.

### 8. 예외 처리 (GlobalExceptionHandler 필수 핸들러)

- 비즈니스 예외: `ai.claudecode.esgt2.common.exception.EsgException` / `ResourceNotFoundException`
- `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 일괄 변환. 메시지는 한국어 + 에러 코드.

**반드시 등록해야 할 핸들러 목록 (누락 시 500 반환)**:

| 예외 | HTTP | 에러 코드 |
|---|---|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `AccessDeniedException` | 403 | `ACCESS_DENIED` |
| `ObjectOptimisticLockingFailureException` | 409 | `OPTIMISTIC_LOCK_CONFLICT` |
| `ExpressionEvaluationException` | 400 | `FORMULA_EVALUATION_FAILED` |
| `FormulaValidationException` | 400 | `FORMULA_VALIDATION_FAILED` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_FAILED` |
| `EsgException` | (에러코드 매핑) | (에러코드 그대로) |

- `AccessDeniedException` 미처리 → Spring 기본 500 반환. **반드시 403으로 처리.**
- `ExpressionEvaluationException`은 서비스 경계에서 `EsgException(FORMULA_EVALUATION_FAILED)`로 변환 후 핸들러 도달.

### 9. 트랜잭션

- Service 메서드 단위 `@Transactional` 명시 (클래스 레벨 금지).
- 읽기 전용: `@Transactional(readOnly = true)`.
- Repository: `@Transactional` 부착 금지.

### 10. Lombok

- 허용: `@RequiredArgsConstructor`, `@Getter`, `@Builder`, `@Slf4j`
- 금지: `@Data`, `@EqualsAndHashCode`, `@ToString`

### 11. 로깅

`@Slf4j`만 사용. 개인정보·민감 데이터(HR 지표·증빙 파일명·공급사 거래 금액 등) 로그 마스킹.

### 12. BigDecimal 산출 정책

배출량·에너지·금액 등 모든 수치 계산에 `float` / `double` 절대 금지. **`BigDecimal`만 사용.**

```java
// 금지
double result = activity * factor;

// 필수
BigDecimal result = activity.multiply(factor).setScale(6, RoundingMode.HALF_UP);
```

- 표준 단위 저장: `DECIMAL(20, 6)` 컬럼 타입 통일.
- `BigDecimal.valueOf(double)` 사용 금지 — `new BigDecimal("1.23")` 또는 `BigDecimal.valueOf(longUnscaledVal, scale)`.

### 13. @Async + @Transactional 분리 원칙 (esg-t1 Phase 8 교훈)

`@Async` 메서드와 `@Transactional` 메서드는 **반드시 분리된 스프링 빈(Bean)에** 선언.
같은 메서드에 두 어노테이션 동시 부착 금지.

```java
// 금지 — 동일 메서드에 @Async + @Transactional
@Async
@Transactional
public void processAsync(Long id) { ... }

// 필수 — 두 빈으로 분리
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

### 14. Scheduler 규칙 (esg-t1 BUG-P4-12, BUG-P4-04)

- `@Scheduled` 어노테이션에 반드시 `zone = "Asia/Seoul"` 명시.
- Scheduler 빈은 반드시 `@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")` 적용.
- `application-test.yml`에 `scheduler.enabled: false` 설정 → 테스트 환경에서 자동 비활성화.

```java
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class EmissionFactorSyncScheduler {
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void sync() { ... }
}
```

### 15. Append-only Repository 패턴 (esg-t1 BUG-P3-07)

`AuditLog`, `DataPointVersion`, `CalculationResult`, `DisclosureSnapshot`처럼 불변성이 필요한 엔티티는 `JpaRepository` 대신 **`Repository<T, ID>` 마커 인터페이스**를 상속.
`delete*`, `deleteAll*`, `save*(iterable)` 메서드가 컴파일 타임에 노출되지 않음.

```java
// 불변 엔티티용 Repository
public interface AuditLogRepository extends Repository<AuditLogEntity, UUID> {
    AuditLogEntity save(AuditLogEntity entity);
    Optional<AuditLogEntity> findById(UUID id);
    // delete 메서드 의도적으로 노출하지 않음
}
```

### 16. 객체 스토리지 경로 순회 방어 (esg-t1 BUG-P3-09)

파일 업로드·다운로드 시 경로 순회(Path Traversal) 공격을 반드시 차단.

```java
public Path resolveContained(Path storageRoot, String filename) {
    Path resolved = storageRoot.resolve(filename).normalize();
    if (!resolved.startsWith(storageRoot)) {
        throw new EsgException(EsgErrorCode.INVALID_FILE_PATH);
    }
    return resolved;
}
```

- 파일 확장자 허용 목록(allowlist): `pdf, xlsx, xls, csv, png, jpg, jpeg` — 그 외 거부.
- 파일명은 UUID로 재생성 후 저장 (원본 파일명은 `original_filename` 컬럼에 별도 보관).

### 17. Flyway 멀티 로케이션 전략 (esg-t1 BUG-P5-09)

- `db/migration` — H2 · PostgreSQL 공통 DDL
- `db/migration-pg` — PostgreSQL 전용 DDL (RLS 정책, 파티션, PG 함수 등)
- 운영 프로파일(`prod`)에서는 두 위치 모두 포함:

```yaml
# application-prod.yml
spring:
  flyway:
    locations:
      - classpath:db/migration
      - classpath:db/migration-pg
```

- `application-test.yml`에서는 `db/migration`만 포함 (H2 호환).
- PostgreSQL 전용 DDL을 공통 위치에 넣으면 H2 테스트에서 파싱 오류 발생 → 반드시 분리.

### 18. 검증 우선 원칙 (esg-t1 BUG-P3-04)

도메인 객체 생성 전에 입력값 유효성 검증을 완료. `create()` 내부에서 검증 실패 시 예외를 던지되, 서비스 레이어에서 검증을 `create()` 호출 이전에 수행.

```java
// 서비스 레이어
public DataPointId create(CreateDataPointCommand cmd) {
    validateCommand(cmd);                  // 1. 먼저 검증
    DataPoint domain = DataPoint.create(cmd);  // 2. 그 후 생성
    ...
}
```

- `ERROR` severity 검증 규칙 위반 → 저장 차단 (`ValidationException` throw).
- `WARNING` severity → 저장은 허용하되 경고 플래그 세팅.

### 19. TestContainers 초기화 순서 (esg-t1 통합 테스트 교훈)

`@DynamicPropertySource` 사용 전에 컨테이너가 반드시 시작되어 있어야 함.

```java
static final PostgreSQLContainer<?> POSTGRES =
    new PostgreSQLContainer<>("postgres:18");

static {
    POSTGRES.start();   // @DynamicPropertySource보다 먼저 실행
}

@DynamicPropertySource
static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
}
```

---

## 테스트 정책 (반드시 준수)

| 대상 | 종류 | 도구 |
|---|---|---|
| Domain, Value Object, 순수 로직 | Unit | JUnit 5 + AssertJ. **JPA 사용 금지.** |
| Service (`DefaultXxxService`) | 통합 | `@SpringBootTest` + TestContainers PostgreSQL. **Mock 라이브러리 금지.** |
| Repository | 통합 | `@DataJpaTest` 또는 `@SpringBootTest` |
| Controller | 통합 | `@SpringBootTest` + `MockMvc` |
| Spring Modulith 경계 | 아키텍처 | `ModularityTest` — 모듈 간 의존 방향 검증 |

- 테스트 메서드명: **한국어 + underscore** (시나리오 그대로 표현).
- `@Transactional` + `@Sql(cleanup)` 으로 테스트 격리. FK 순서대로 DELETE.
- 모든 기능 추가·수정에 **반드시 테스트 동반**. 테스트 없는 변경 거절.

### TDD 사이클 (필수)

1. **Red** 🔴 — 실패하는 테스트 먼저 작성
2. **Green** 🟢 — 통과하는 최소 구현
3. **Refactor** 🔵 — 통과 유지하며 개선

커밋 prefix: `test:` → `feat:` → `refactor:`

---

## 개발 워크플로우

- `docs/task.md` Phase별 체크리스트 유지. 완료 즉시 체크.
- Phase 종료 시 `docs/code-review.md`에 리뷰 기록.
- 이슈 발견 즉시 `docs/fix.md` 등록 후 TDD로 해결.
- 학습 인사이트는 `docs/insight.md`에 누적.

---

## 문서 구조

```
docs/
├── regulatory.md   # ESG 규제 현황 레퍼런스 (주기적 업데이트)
├── prd.md          # 제품 요구사항
├── spec.md         # 기술 명세
├── plan.md         # 실행 계획 (Phase 0~14)
├── task.md         # Phase별 작업 체크리스트
├── code-review.md  # 코드 리뷰 기록
├── fix.md          # 이슈 체크리스트
├── insight.md      # 학습 인사이트 누적
├── openapi.yml     # 빌드 시 자동 생성
└── adr/            # Architecture Decision Records
    ├── ADR-001-spring-modulith.md
    ├── ADR-002-auditable-aop.md
    └── ADR-003-scope3-engine.md
```
