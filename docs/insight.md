# ESG 공시지원 시스템 esg-t2 — 개발 인사이트 (Insight)

> **목적**: 각 Phase 개발 과정에서 얻은 교훈, 설계 결정의 이유, 예상치 못한 복잡성을 기록한다.  
> **갱신**: 각 Phase 완료 시 담당자가 직접 추가  
> **레퍼런스**: esg-t1/docs/insight.md (계승 교훈 포함)

---

## esg-t1 계승 교훈 (Phase 0 시작 전 숙지 필수)

### L-0-01: AuditTrail 누락은 조기 감지가 핵심 (from BUG-P6-02)

**현상**: esg-t1에서 AuditTrail 기록을 서비스 레이어에서 수동 호출 방식으로 구현했다가, 일부 신규 서비스 메서드에서 호출 누락 발생.

**교훈**: `@Auditable` AOP를 Phase 2 초반에 완성하고, 이후 모든 데이터 변경 메서드에 어노테이션을 붙이는 것을 코드 리뷰 체크리스트로 강제한다. 누락은 런타임에서 발견되지 않으므로, 테스트에서 "AuditLog 1건 이상 생성됨" 검증을 항상 포함한다.

**esg-t2 적용**: Phase 2 DoD에 `@Auditable 없으면 빌드 경고` 규칙 포함 고려.

---

### L-0-02: YAML 로더 멱등성 — 파일 레벨이 아닌 항목 레벨 (from BUG-P5-07)

**현상**: esg-t1에서 YAML 배출계수 파일을 이미 처리된 경우 전체 파일 스킵 → 새 항목이 추가된 파일의 신규 항목이 로드되지 않는 버그.

**교훈**: "이 파일은 이미 처리됨"이라는 파일 레벨 체크는 잘못된 추상화다. 항상 항목(row) 레벨 `ON CONFLICT DO UPDATE`로 멱등성을 보장해야 한다.

**esg-t2 적용**: T-3-06에서 item-level upsert만 허용. 파일 레벨 스킵 코드는 PR 리뷰에서 거부.

---

### L-0-03: Domain ≠ Entity — 서비스에서 JPA Entity 직접 생성 금지

**현상**: esg-t1 초기에 서비스 레이어에서 `JpaEntity.builder()...build()`로 직접 객체 생성 → 도메인 유효성 검사 우회.

**교훈**: 도메인 팩토리 메서드(`DomainObject.create(cmd)`)만 통해 도메인 객체를 생성하고, 매퍼가 도메인 → JPA Entity 변환을 담당한다.

**esg-t2 적용**: CLAUDE.md에 명시, 코드 리뷰 필수 체크 항목.

---

### L-0-04: `synchronized` + `@Transactional` 조합 위험

**현상**: esg-t1에서 해시 체인 계산 시 `synchronized` 메서드에 `@Transactional`을 함께 사용했다가, 트랜잭션 커밋 전에 락이 해제되어 레이스 컨디션 발생.

**교훈**: DB 레벨 락(`PESSIMISTIC_WRITE` 또는 `SELECT FOR UPDATE`)을 사용해 트랜잭션 경계 내에서 순서를 보장한다.

**esg-t2 적용**: T-2-10에서 PESSIMISTIC_WRITE 락 사용.

---

### L-0-05: `@TestConfiguration` vs `@Configuration` 혼동 주의

**현상**: esg-t1 통합 테스트에서 `@Configuration`으로 테스트 전용 빈을 등록했다가 프로덕션 컨텍스트 오염.

**교훈**: 테스트 전용 빈은 반드시 `@TestConfiguration`으로 등록. `@Configuration`은 프로덕션 빈만.

**esg-t2 적용**: AbstractIntegrationTest에서 `@TestConfiguration` 패턴 예시 포함.

---

### L-0-06: 증빙 파일 업로드 — DigestInputStream으로 I/O 1회 처리 (from esg-t1 Phase 3)

**현상**: 파일 업로드 시 SHA-256 해시를 계산하려면 파일 전체를 읽어야 하고, 저장소에 쓸 때 다시 읽으면 I/O가 2회 발생한다.

**교훈**: `DigestInputStream`(Java 표준 라이브러리)으로 입력 스트림에 해시 계산을 래핑하면, Object Storage에 쓰는 동시에 SHA-256이 계산된다. I/O 1회로 업로드와 무결성 검증을 동시에 처리.

**esg-t2 적용**: T-3B-04 파일 업로드 구현 시 `DigestInputStream` 패턴 필수.

---

### L-0-07: H2 예약어 컬럼명 충돌 — 운영/테스트 DB 불일치 (from esg-t1 Phase 2)

**현상**: `YEAR`, `MONTH`, `VALUE` 등 SQL 예약어를 컬럼명으로 쓰면 PostgreSQL에서는 괜찮지만 H2(테스트 보조)에서 실패한다.

**교훈**: `reporting_year`, `data_value`, `ef_value`처럼 의미를 나타내는 접두사를 붙인다. esg-t2 스키마 설계 시 H2 예약어 목록 사전 확인.

**esg-t2 적용**: 스키마 컬럼명 네이밍 코드 리뷰 체크 항목에 추가.

---

### L-0-08: Canonical JSON — Hash Chain 기록과 검증이 동일 직렬화 로직 공유 필수 (from esg-t1 Phase 1-D)

**현상**: AuditLog 저장 시 직렬화한 JSON과 무결성 검증 시 직렬화한 JSON이 필드 순서·null 처리가 달라 해시 불일치 → 항상 무결성 오류 발생.

**교훈**: `CanonicalJson.buildMap()` 같은 단일 정적 메서드를 만들어 기록 경로와 검증 경로가 반드시 같은 함수를 호출하도록 강제한다.

**esg-t2 적용**: `HashChainCalculator`에 `canonicalPayload()` 단일 메서드. 두 경로(저장/검증)에서 공통 호출.

---

### L-0-09: `factorAt(code, date)` — 과거 산출 시점의 배출계수 재현 필수 (from esg-t1 spec)

**현상**: 배출계수를 갱신하면 과거 공시 수치가 자동으로 변경되어 재현성을 위반한다.

**교훈**: `EmissionFactor`에 `effective_from / effective_to` 유효기간 컬럼을 두고, 산출 시점의 계수를 조회하는 `factorAt(code, date)` 함수를 구현한다. 과거 공시는 당시 계수로 항상 동일하게 재현 가능해야 한다.

**esg-t2 적용**: `EmissionFactorResolver.resolveAt(category, date)` — Phase 3에서 구현 필수.

---

### L-0-10: `@PreAuthorize` 예외는 `@RestControllerAdvice`에서 명시적으로 처리 (from esg-t1 Phase 1-C)

**현상**: `AccessDeniedException`을 `@RestControllerAdvice`에서 처리하지 않으면 500 응답이 반환된다.

**교훈**: `@ExceptionHandler(AccessDeniedException.class)`를 `GlobalExceptionHandler`에 명시적으로 등록하고 403 응답을 반환한다.

**esg-t2 적용**: Phase 1에서 GlobalExceptionHandler 작성 시 포함 필수.

---

### L-0-11: `@Async` + `@Transactional` 동일 메서드 사용 금지 (from esg-t1 Phase 8)

**현상**: 같은 메서드에 `@Async`와 `@Transactional`을 함께 선언하면, Spring의 프록시 메커니즘 특성상 두 AOP 중 하나가 적용되지 않거나 예기치 않은 순서로 동작한다. 트랜잭션이 커밋되기 전에 비동기 컨텍스트가 종료되거나, 반대로 비동기 실행이 동기처럼 동작하는 문제가 발생한다.

**교훈**: `@Async` 메서드는 다른 빈의 `@Transactional` 메서드를 호출하는 디스패처 역할만 수행한다. 두 어노테이션을 절대 같은 메서드에 부착하지 않는다.

**esg-t2 적용**: CLAUDE.md 섹션 13에 규칙 명시. 코드 리뷰에서 동일 메서드 조합 차단.

---

### L-0-12: BigDecimal 전용 산출 정책 (from esg-t1 Phase 5 배출량 계산 버그)

**현상**: `double` 타입으로 배출계수(예: 0.1)와 활동량을 곱하면, IEEE 754 부동소수점 오차가 누적되어 소수점 이하 정밀도가 떨어진다. 수천 건의 공급망 데이터를 집계할 때 수 tCO2e 오차가 발생했다.

**교훈**: 배출량·에너지·금액 등 모든 수치 계산에 `BigDecimal`을 사용한다. 특히 `EmissionCalculator`, `UnitConverter`, `ConsolidationEngine`에서 `double` 타입을 완전히 제거한다.

**esg-t2 적용**: CLAUDE.md 섹션 12에 BigDecimal 전용 규칙 명시. Phase 12 전수 검증.

---

### L-0-13: CSV 대량 업로드 — Row-level 독립 트랜잭션 (from esg-t1 Phase 3 BUG-P3-11)

**현상**: CSV 100행을 단일 트랜잭션으로 처리할 때, 99번째 행의 유효성 오류로 인해 처음 98행도 모두 롤백됐다. 사용자가 전체 파일을 재업로드해야 했고, 멱등성 보장이 없으면 다시 중복 오류가 발생했다.

**교훈**: 각 행을 `Propagation.REQUIRES_NEW` 트랜잭션으로 독립 처리한다. 오류 행은 결과 리포트에 기록하고 나머지 행은 계속 처리한다.

**esg-t2 적용**: Phase 6 CSV 업로드 구현 시 `ActivityDataRowImporter` 별도 빈 + `REQUIRES_NEW` 패턴 적용.

---

### L-0-14: Append-only Repository — `Repository<T,ID>` 마커 인터페이스 (from esg-t1 BUG-P3-07)

**현상**: `AuditLogJpaRepository`가 `JpaRepository`를 상속하면 `deleteById()`, `deleteAll()` 메서드가 노출된다. 실수로 서비스에서 호출해도 컴파일 오류 없이 실행된다.

**교훈**: 불변 엔티티의 Repository는 `Repository<T,ID>` 마커 인터페이스(Spring Data 제공)를 상속하고 필요한 메서드만 선언한다. `delete*` 메서드는 컴파일 타임에 아예 노출되지 않는다.

**esg-t2 적용**: `AuditLogRepository`, `CalculationResultRepository`, `VerificationSnapshotRepository`에 적용.

---

### L-0-15: Scheduler `zone` + 테스트 격리 (from esg-t1 BUG-P4-12, BUG-P4-04)

**현상 1 (BUG-P4-12)**: `@Scheduled(cron = "0 0 2 * * *")`에 시간대를 지정하지 않으면 서버 JVM 기본 시간대(UTC)로 동작해, KST(UTC+9) 기준 예상 실행 시각과 9시간 차이가 발생했다.

**현상 2 (BUG-P4-04)**: Testcontainers 통합 테스트 중 스케줄러가 활성화되어 테스트 데이터 위에서 실제 스케줄 작업이 실행됐다. 테스트 결과가 비결정적으로 되었다.

**교훈**: `@Scheduled(cron = "...")` 사용 시 반드시 `zone = "Asia/Seoul"`을 명시. `fixedDelay`/`fixedRate`는 절대 시각이 아닌 상대 간격이므로 `zone` 미적용 (`zone` 속성이 없음 — 붙이면 컴파일 오류). 스케줄러 빈에는 `@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")`를 적용하고 `application-test.yml`에 `scheduler.enabled: false`를 설정한다.

**esg-t2 적용**: CLAUDE.md 섹션 14에 양쪽 규칙 모두 명시.

---

### L-0-16: 승인 상태 기계 — 명시적 전이 메서드 (from esg-t1 Phase 7 설계 검토)

**현상**: 승인 엔티티의 상태를 `entity.setStatus("APPROVED")` 형태로 직접 변경하면, 유효하지 않은 전이(REJECTED → APPROVED 직접 전환)가 컴파일 타임에 방지되지 않는다. 버그 리포트가 없어도 DB에 불일치 상태가 조용히 저장된다.

**교훈**: `approve()`, `reject(reason)`, `escalate()` 명시적 전이 메서드만 노출한다. 각 메서드에서 현재 상태 유효성을 검사하고 허용되지 않는 전이는 예외를 던진다. `reason` 필드는 빈 문자열 불가.

**esg-t2 적용**: spec.md 섹션 8 승인 상태 기계 설계에 포함. Phase 7 구현 시 적용.

---

## Phase 0: 프로젝트 셋업

### L-P0-01: Spring Boot 4에서 TestRestTemplate·@AutoConfigureMockMvc 제거

**현상**: `org.springframework.boot.test.web.client.TestRestTemplate` 및 `@AutoConfigureMockMvc` 어노테이션이 Spring Boot 4에서 완전히 제거되어 컴파일 오류 발생.

**교훈**: Spring Boot 4 통합 테스트에서는 `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()` 패턴을 사용한다. `TestRestTemplate` 없이도 `MockMvc`로 HTTP Basic 인증, 응답 상태 검증이 가능하다.

**esg-t2 적용**: `ActuatorEndpointTest` 및 이후 Controller 테스트 전체에 `MockMvcBuilders` 패턴 적용.

---

### L-P0-02: Spring Modulith 2.0.0 — event_publication 필수 컬럼 2개

**현상**: Spring Modulith 2.0.0에서 `event_publication` 테이블에 `last_resubmission_date`와 `status` 두 컬럼이 모두 필요하다. `ddl-auto: validate`를 사용할 경우 두 컬럼 중 하나라도 없으면 기동 실패.

**교훈**: Spring Modulith 버전 업그레이드 시 `event_publication` 스키마 변경사항을 반드시 Release Notes에서 확인한다. `ddl-auto: none`으로 설정하면 검증 자체를 건너뛰지만, Flyway V1에 두 컬럼을 모두 포함하는 것이 장기적으로 안전하다.

**esg-t2 적용**: V1에 `status VARCHAR(36)`, `last_resubmission_date TIMESTAMP WITH TIME ZONE` 포함.

---

### L-P0-03: Docker Desktop 29.x + TestContainers 1.21.0 — TCP + API 버전 강제

**현상**: TestContainers 1.21.0(내부 docker-java)은 Docker API 1.23을 기본으로 사용하지만, Docker Desktop 29.4.3은 최소 API 1.40을 요구해 `/v1.23/info`에서 HTTP 400 Empty Body 오류 발생.

**해결 3단계**:
1. Docker Desktop Settings → "Expose daemon on tcp://localhost:2375 without TLS" 활성화
2. Windows 사용자 환경변수에 `DOCKER_HOST=tcp://localhost:2375` 설정
3. `build.gradle.kts`의 Test 태스크에 `systemProperty("api.version", "1.40")` 추가 + `DOCKER_HOST` 패스스루

**교훈**: Windows에서 TestContainers + Docker Desktop 최신 버전 조합 시 이 3단계 설정이 모두 필요하다. 환경변수는 Gradle 데몬 재시작 후에만 반영된다.

**esg-t2 적용**: `build.gradle.kts` test 태스크에 영구 적용.

---

### L-P0-04: H2 + PostgreSQL 드라이버 충돌 — @DynamicPropertySource driver-class-name 오버라이드

**현상**: `application.yml`에 `driver-class-name: org.h2.Driver`가 설정된 상태에서 `@DynamicPropertySource`로 PostgreSQL JDBC URL만 교체하면 H2 드라이버가 PostgreSQL URL을 처리하려다 실패.

**교훈**: `@DynamicPropertySource`에서 URL 교체 시 `spring.datasource.driver-class-name`도 함께 `org.postgresql.Driver`로 오버라이드해야 한다.

**esg-t2 적용**: `AbstractIntegrationTest.configureProperties()`에 드라이버 오버라이드 포함.

---

### L-P0-05: H2 shutdown 시 Spring Modulith event_publication WARN — 무해한 경고

**현상**: `ddl-auto: create-drop` 환경(H2)에서 JVM 종료 시 Spring Modulith `DefaultEventPublicationRegistry` destroy callback이 `event_publication`을 SELECT하려 하지만, Hibernate가 이미 DROP TABLE을 실행한 후라 HHH000247 WARN이 발생.

**교훈**: 이는 테스트 실패가 아닌 순수 경고이며, Spring Modulith와 Hibernate DDL-Auto 생명주기 순서의 알려진 특성이다. CI/운영(PostgreSQL + Flyway)에서는 이 현상이 발생하지 않는다.

**esg-t2 적용**: 테스트 결과에서 이 WARN은 무시한다.

---

### L-P0-06: Spring Modulith 2.0 — @ApplicationModule 없어도 직하위 패키지 자동 모듈 인식

**현상**: `shared/` 패키지에 `@ApplicationModule`을 부착하지 않았는데, `ModularityTest`에서 모듈 수가 6개가 아닌 7개로 카운트됨.

**교훈**: Spring Modulith 2.0은 최상위 앱 패키지(`ai.claudecode.esgt2`) 직하위의 모든 패키지를 자동으로 모듈로 인식한다. `@ApplicationModule`이 없어도 `shared/`, `ghg/` 등이 모두 모듈로 등록된다. 모듈 경계 테스트에서 "N개가 등록된다"는 assertion을 쓸 때 반드시 실제 패키지 수를 먼저 확인해야 한다.

**esg-t2 적용**: `ModularityTest.모듈_7개가_등록된다()` — ghg, entity, audit, vw, rpt, supply, shared.

---

## Phase 1: 법인·테넌트 관리

### L-P1-01: Spring Modulith — 하위 패키지는 @NamedInterface로 공개 선언 필수

**현상**: `entity.domain.LegalEntity`에서 `shared.exception.EsgException`을 import하자 `ModularityTest.모듈_경계가_유효하다`가 실패. Spring Modulith는 모듈의 하위 패키지(`shared.exception`, `shared.security`)를 기본적으로 **internal**(비공개)로 취급한다.

**교훈**: 다른 모듈에서 접근해야 하는 하위 패키지에는 `package-info.java`에 `@NamedInterface`를 선언해야 한다. 루트 패키지만 자동으로 공개된다.

**esg-t2 적용**: `shared.exception`, `shared.security`, `shared.tenant`, `shared.web` 패키지에 각각 `@NamedInterface` 부착.

---

### L-P1-02: Spring Security `requestMatchers().hasRole()` vs `@PreAuthorize` — GlobalExceptionHandler 활성화 차이

**현상**: `SecurityConfig`에 `requestMatchers(POST, "/api/v1/entities").hasRole("TENANT_ADMIN")`을 설정하면, 필터 레벨에서 차단이 일어나 `accessDeniedHandler`가 `sendError(403)`을 호출한다. 이 경우 응답 본문이 없어서 `GlobalExceptionHandler.handleAccessDenied()`가 JSON 응답을 반환하지 못한다.

**교훈**: RBAC를 `GlobalExceptionHandler`의 JSON 형식으로 반환하려면 세밀한 역할 제한은 `@PreAuthorize` 어노테이션으로 구현해야 한다. `SecurityConfig`에는 인증 여부(`authenticated()`)만 설정하고, 역할 기반 접근 제어는 컨트롤러 메서드 `@PreAuthorize`에 위임한다.

**esg-t2 적용**: SecurityConfig는 `anyRequest().authenticated()`만 설정. 모든 컨트롤러 메서드에 `@PreAuthorize` 필수.

---

### L-P1-03: TenantContextInterceptor — `SET LOCAL` 대신 `set_config()` 파라미터 바인딩

**현상**: `"SET LOCAL app.current_tenant_id = '" + tenantId + "'"` 형태는 SQL Injection 취약점. tenantId가 `UUID.fromString()`을 통과했어도 보안 원칙상 문자열 연결은 금지.

**교훈**: PostgreSQL `set_config(setting, value, is_local)` 함수를 사용하면 `?` 파라미터 바인딩으로 SQL Injection을 방어한다. `is_local = true`로 설정하면 `SET LOCAL`과 동일한 트랜잭션 범위 격리가 적용된다.

**esg-t2 적용**: `SELECT set_config('app.current_tenant_id', ?, true)` + `JdbcTemplate.queryForObject(...)` 패턴 표준화.

---

## Phase 2: AuditLog & Hash Chain

_Phase 완료 후 내용 추가 예정_

---

## Phase 3: 배출계수 & Scope 1/2

_Phase 완료 후 내용 추가 예정_

---

## Phase 4: 다법인 연결 집계

_Phase 완료 후 내용 추가 예정_

---

## Phase 5: Scope 3 계산

_Phase 완료 후 내용 추가 예정_

---

## Phase 6: 데이터 수집 파이프라인

_Phase 완료 후 내용 추가 예정_

---

## Phase 7: 공시 보고서 생성

_Phase 완료 후 내용 추가 예정_

---

## Phase 8: 외부 검증 워크스페이스

_Phase 완료 후 내용 추가 예정_

---

## Phase 9~11: 프론트엔드

_Phase 완료 후 내용 추가 예정_

---

## Phase 12: 통합 검증

_Phase 완료 후 내용 추가 예정_
