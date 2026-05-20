# ESG 공시지원 시스템 esg-t2 — 코드 리뷰 체크리스트 & 이력 (Code Review)

> **목적**: 코드 리뷰에서 반복적으로 확인해야 할 항목을 정의하고, 주요 리뷰 결과를 기록한다.  
> **갱신**: 새로운 공통 이슈 발견 시 체크리스트에 항목 추가

---

## 1. 필수 체크리스트 (PR 생성 전 자가 점검)

### 1.1 도메인 설계

- [ ] 서비스에서 `JpaEntity.builder()...build()` 직접 호출 없음 (도메인 팩토리만 허용)
- [ ] 도메인 객체는 불변(record 또는 final 필드) 또는 명시적 변경 메서드만 존재
- [ ] `DomainObject.create(cmd)` 팩토리 메서드에 유효성 검사 포함

### 1.2 @Auditable AOP

- [ ] 데이터 변경 메서드(create/update/delete/approve)에 `@Auditable(action = "...")` 부착
- [ ] @Auditable 통합 테스트: `AuditLog` 1건 이상 생성됨 검증 포함

### 1.3 테스트

- [ ] 서비스 레이어 통합 테스트에서 Mock DB 사용 없음 (Testcontainers PostgreSQL 사용)
- [ ] 도메인 단위 테스트에서 경계값(null, 빈값, 최대값) 케이스 포함
- [ ] TDD 순서 준수: `test:` 커밋 먼저, `feat:` 커밋 나중

### 1.4 Spring Modulith 경계

- [ ] 다른 모듈의 `internal` 패키지 직접 참조 없음
- [ ] 모듈 간 비동기 통신 → `ApplicationEventPublisher` 사용
- [ ] `ModularityTest` 로컬에서 통과 확인

### 1.5 보안

- [ ] 사용자 입력 → SQL 쿼리에 PreparedStatement 사용 (문자열 연결 금지)
- [ ] API 응답에 비밀번호·토큰·개인식별정보 노출 없음
- [ ] 역할 기반 `@PreAuthorize` 적용 (인가 없는 엔드포인트 없음)
- [ ] 크로스 테넌트 데이터 접근 방지: RLS 정책 + 애플리케이션 레벨 이중 방어

### 1.6 YAML 로더 (배출계수)

- [ ] 파일 레벨 스킵 로직 없음 (`already_processed` 파일 체크 금지)
- [ ] 항목 레벨 upsert: 값 변경 없으면 스킵, 값 변경 시 비활성화+새 INSERT (UPDATE in-place 금지)
- [ ] 배출계수 갱신 시 새 레코드 `effective_from = 오늘` 사용 (YAML의 effective_from 아님)

### 1.7 Hash Chain

- [ ] AuditLog 저장 시 PESSIMISTIC_WRITE 락 사용
- [ ] `synchronized` + `@Transactional` 조합 없음

### 1.8 증빙 파일 (Evidence)

- [ ] 파일 업로드 시 `DigestInputStream` 사용 (I/O 1회 = 업로드 + SHA-256 동시)
- [ ] `evidence_files`에 저장 전 SHA-256 무결성 검증 완료
- [ ] Object Storage URI는 DB에 저장, 파일 자체는 DB에 저장하지 않음
- [ ] 활동 데이터 삭제 금지 — 증빙과 연결된 데이터는 비활성화 처리

### 1.9 정정·재공시

- [ ] `activity_data` 정정 시 `reason_code` 필수 — 빈 문자열 불가
- [ ] 정정 메서드는 새 버전 INSERT만 수행 (UPDATE/DELETE 없음)
- [ ] 정정 후 재산출 이벤트 발행 확인

### 1.10 단위 변환

- [ ] 활동 데이터 저장 시 원 단위(`unit`)와 변환 단위(`standard_value`, `standard_unit`) 모두 저장
- [ ] `UnitConverter.convert()` 단일 메서드 경유 — 직접 계산 금지

### 1.11 코드 품질

- [ ] 메서드 길이 ≤ 30행 (초과 시 분리 검토)
- [ ] 의미 없는 주석(WHAT) 없음; WHY가 자명하지 않을 때만 주석
- [ ] `Default*` 네이밍: 인터페이스 구현체의 기본 구현에 사용
- [ ] 컬럼명에 SQL 예약어 사용 없음 (`year` → `reporting_year`, `value` → `data_value`)

---

## 2. 리뷰 이력

> 형식: `YYYY-MM-DD | 범위 | 핵심 발견 사항`

### 2026-05-19 | Phase 0 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| 모듈 패키지 구조 | ✅ | `@ApplicationModule` 6개 모듈, `package-info.java` 구조 정상 |
| `ModularityTest` | ✅ PASS | 모듈 경계 위반 없음 |
| CI 파이프라인 | ✅ | `.github/workflows/ci.yml` — Java 25, TESTCONTAINERS_RYUK_DISABLED |
| Flyway 멀티 로케이션 | ✅ | `db/migration` + `db/migration-pg` 분리 |
| V1 스키마 | ✅ | `event_publication.status` 컬럼 추가 (Spring Modulith 2.0.0 필수) |
| Spring Boot 4 호환성 | ✅ | `TestRestTemplate` 제거 → `MockMvcBuilders` 교체 완료 |
| 테스트 결과 | ✅ **9 tests, 0 failures** | ActuatorEndpointTest, FlywayMigrationTest, ModularityTest, AbstractIntegrationTestTest, Esgt2ApplicationTest |
| Redis 비활성화 전략 | ✅ | `management.health.redis.enabled=false` + Redis AutoConfig exclude |
| Docker TCP 설정 | ✅ | `DOCKER_HOST=tcp://localhost:2375`, `api.version=1.40` |

**개선 필요 사항 (Phase 1 착수 전)**:
- `event_publication` 테이블 H2 shutdown WARN — 허용 가능한 경고 (Spring Modulith destroy callback 순서 문제). 테스트 실패 아님.
- T-0-12 (Next.js) — UI 개발 게이트 대기 중 (사용자 승인 필요)

### 2026-05-19 | Phase 1 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| Domain≠Entity 원칙 | ✅ | LegalEntity, EntityRelationship 순수 Java record. JPA entity 분리 완료 |
| `DomainObject.create(cmd)` 팩토리 | ✅ | 서비스에서 domain factory 경유, JpaEntity.builder() 직접 호출 없음 |
| 검증 우선 원칙 | ✅ | `create()` 호출 전 파라미터 검증 완료 (ValidationFirstPrincipleTest 3건) |
| EntityRelationshipGraph DAG 사이클 탐지 | ✅ | DFS + inStack 방식, EsgException(VALIDATION_FAILED) 발생 |
| JWT 인증 흐름 | ✅ | NimbusJwtEncoder/Decoder, 15분/7일 토큰, Redis 블랙리스트 |
| TenantContextInterceptor set_config() | ✅ | SQL 파라미터 바인딩 사용, 문자열 연결 없음 (L-P1-03 반영) |
| @NamedInterface 선언 | ✅ | shared/exception, security, tenant, web 4개 패키지 선언 (L-P1-01 반영) |
| SecurityConfig vs @PreAuthorize | ✅ | 역할 matchers 제거, 전부 @PreAuthorize로 통일 (L-P1-02 반영) |
| ModularityTest | ✅ PASS | 모듈 경계 위반 없음 |
| 전체 테스트 | ✅ **47 tests, 0 failures** | Phase 0(9) + Phase 1(38) |

**Phase 1 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `JwtTokenProvider.secretKey()` 매 요청마다 SecretKey 재생성 | P2 | explicit constructor로 `secretKey` + `jwtDecoder` 캐싱 |
| `JwtAuthenticationFilter` catch(Exception) 광범위 처리 | P2 | `JwtException` 별도 catch → 나머지 warn 로그 |
| `DefaultAuthService.refresh()` `@Transactional` 누락 | P2 | `@Transactional(readOnly = true)` 추가 |
| `DefaultEntityManagementService` 와일드카드 임포트 | P3 | 명시적 임포트로 교체 |
| DTO 전체 `@Schema` 어노테이션 미적용 | P3 | `CreateEntityRequest`, `EntityResponse` 등 8개 DTO에 `@Schema` 추가 |
| `EntityControllerSecurityTest` TENANT_ADMIN 테스트 NPE | P2 | `@WithMockJwtUser` 테스트 어노테이션 생성, 테스트 교체 |

**잔존 기술 부채 (Phase 2 이후 해소 예정)**:

| 항목 | 우선순위 | 내용 |
|---|---|---|
| `DefaultAuthService.blacklist()` Redis key | P3 | 전체 JWT 토큰 문자열을 key로 사용 중 (300~500자). JTI claim 추가 후 JTI를 key로 교체 필요 |
| `application.yml` JWT secret | P2 | 개발용 하드코딩 secret. 운영 환경은 `JWT_SECRET` 환경변수 오버라이드 필수 |
| `@Auditable` AOP 미적용 | P1 | `entity.create`, `setRelationship` 메서드에 Phase 2 구현 후 부착 (Phase 2에서 해소) |

### 2026-05-19 | Phase 2 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| `@Auditable` AOP (AuditAspect) | ✅ | `@Aspect` + `@Around`, Outbox INSERT 정상 |
| Hash Chain — PESSIMISTIC_WRITE 락 | ✅ | `findFirstByTenantIdOrderByIdDesc()` `@Lock(PESSIMISTIC_WRITE)` |
| Canonical JSON 단일 직렬화 | ✅ | `HashChainCalculator.canonicalPayload()` — TreeMap 키 정렬 보장 |
| `@ConditionalOnProperty(scheduler.enabled)` | ✅ | 두 스케줄러 모두 조건부 |
| Outbox Poller — `fixedDelay` (zone 미사용) | ✅ | 09-scheduler.md 규칙 준수 |
| 무결성 검증 스케줄러 — `zone=Asia/Seoul` | ✅ | 09-scheduler.md 규칙 준수 |
| DB 인덱스 | ✅ | `idx_audit_logs_tenant`, `idx_outbox_status` 생성 |
| 통합 테스트 4건 | ✅ PASS | Outbox 생성, AuditLog 생성, Hash Chain 연결, 비감사 메서드 확인 |

**Phase 2 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `AuditLogRepository extends JpaRepository` — delete 메서드 노출 | **P0** | `Repository<T,ID>`로 교체, 명시적 메서드만 선언 (08-persistence.md append-only 원칙) |
| `audit_logs` RLS 정책 + REVOKE 누락 | **P0** | `db/migration-pg/V5__audit_rls.sql` 생성 — `ENABLE ROW LEVEL SECURITY` + `REVOKE UPDATE, DELETE` |
| `AuditAspect` try-catch로 Outbox 저장 실패 삼킴 | **P0** | try-catch 제거 — Outbox 실패 시 @Transactional 롤백 보장 |
| `AuditIntegrityScheduler` — `@Transactional` 직접 부착 | **P1** | `AuditIntegrityService` 별도 빈 생성, 스케줄러는 위임만 수행 (09-scheduler.md 규칙) |
| `AuditIntegrityScheduler` — `findAll()` 전체 로드 | **P2** | `findDistinctTenantIds()` JPQL 쿼리로 교체 (성능) |
| `OutboxEventJpaEntity` — `@Setter` on status | **P2** | `@Setter` 제거 — `markProcessed()`, `markFailed()` 명시적 상태 전이 메서드만 허용 (01-domain-architecture.md) |
| `AuditableIntegrationTest.cleanup()` — `auditLogRepository.deleteAll()` 컴파일 오류 | **필수** | `JdbcTemplate.execute("DELETE FROM audit_logs")` 교체 |

**잔존 기술 부채 (Phase 3 이후 해소 예정)**:

| 항목 | 우선순위 | 내용 |
|---|---|---|
| `OutboxProcessingService.processNow()` 단일 트랜잭션 | P2 | 모든 Outbox 이벤트를 하나의 트랜잭션으로 처리. 5번째 이벤트 실패 시 DB 상태가 오염될 수 있음. `REQUIRES_NEW` 패턴 적용 권장 (05-async-concurrency.md CSV 패턴과 동일) |
| `AuditAspect` vs `@Transactional` 어드바이저 순서 비결정적 | P2 | 명시적 `@Order` 없이 런타임 순서에 의존. `@EnableTransactionManagement(order=0)` + `@Order(1)` on `AuditAspect`로 명시화 권장 |
| `DefaultEntityManagementService` — `LegalEntityJpaEntity.builder()` 직접 호출 | P1 | 서비스에서 JPA Entity 직접 생성 (01-domain-architecture.md 위반). Phase 3 착수 전 Mapper 도입 필요 |
| `OutboxEventJpaEntity.markFailed()` — 에러 메시지 미기록 | P3 | 09-scheduler.md "에러 메시지 기록" 요구사항. `error_message` 컬럼 추가 + `markFailed(String msg)` 오버로드 |

### 2026-05-19 | Phase 3 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| BigDecimal 전용 산출 | ✅ | `EmissionCalculator.computeEmission()` — float/double 없음, scale=6 HALF_UP |
| EmissionFactorResolver — 재현성 | ✅ | `resolveAt(category, subCategory, countryCode, date)` — subCategory 포함 시점 기반 조회 |
| EmissionRecord — append-only | ✅ | `EmissionRecordRepository extends Repository<T,ID>` — delete 메서드 컴파일 타임 차단 |
| ActivityData domain factory + mapper | ✅ | `ActivityData.create(cmd)` + `ActivityDataMapper.toEntity(domain)` — 서비스 JpaEntity.builder() 직접 호출 없음 |
| YAML SNAKE_CASE 매핑 | ✅ | `ObjectMapper.setPropertyNamingStrategy(SNAKE_CASE)` — `sub_category` → `subCategory` |
| @Auditable 부착 | ✅ | `ACTIVITY_DATA_CREATED`, `EMISSIONS_CALCULATED` |
| @PreAuthorize 전수 | ✅ | `ESG_MANAGER`, `ESG_VIEWER`, `VERIFIER` 역할 구분 |
| REST URL 규칙 | ✅ | `/calculations` (동사 금지, 04-api-design.md 준수) |
| 통합 테스트 | ✅ PASS | GhgIntegrationTest 7건, EmissionFactorLoaderTest 3건, EmissionCalculatorTest 7건 |

**Phase 3 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `EmissionFactorResolver.resolveAt()` — subCategory 없어 다중 결과 NonUniqueResultException | **P1** | 인터페이스·구현체·쿼리 모두 `subCategory` 파라미터 추가 |
| `EmissionFactorYaml` YAML SNAKE_CASE 미매핑 — subCategory 등 null 파싱 | **P1** | `ObjectMapper(YAMLFactory)` 에 `SNAKE_CASE` 명명 전략 추가 |
| `activity_data` 테이블 `country_code` 컬럼 누락 — 배출계수 조회 불가 | **P1** | `V8__activity_data_country_code.sql` + `ActivityDataJpaEntity`·`Command` 업데이트 |
| `GhgIntegrationTest` 테넌트 FK 위반 (`00000000-...010` 미등록) | **필수** | TENANT_ID `00000000-...001` (V2 씨드)로 변경 |

**잔존 기술 부채 (Phase 3-B 이후 해소 예정)**:

| 항목 | 우선순위 | 내용 |
|---|---|---|
| `activity_data` `standard_value`/`standard_unit` 미채움 | P2 | `UnitConverter` (T-3B-08) 구현 후 연동 필요 |
| Phase 2 부채: `DefaultEntityManagementService` Mapper | P1 | Phase 2에서 이월 — Phase 3-B 착수 전 해소 권장 |

### 2026-05-20 | Phase 3 상세 리뷰 추가 수정

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `DefaultGhgService.calculateEmissions()` — `EmissionRecordJpaEntity.builder()` 직접 호출 | **P1** | `EmissionRecord` 도메인 record + `EmissionRecordMapper` 생성. 서비스는 `EmissionRecord.calculate()` 팩토리 + `EmissionRecordMapper.toEntity()` 경유 |
| `emission_records` 테이블 — `db/migration-pg` RLS + REVOKE 누락 | **P1** | `V9__emission_records_rls.sql` 생성 — `ENABLE ROW LEVEL SECURITY` + `REVOKE UPDATE, DELETE` (audit_logs와 동일 패턴) |
| `DefaultEmissionFactorResolver` — `@Component` 사용 (명명 규칙 불일치) | P3 | `@Service`로 교체 (`Default*` 구현체는 `@Service` 통일) |

전체 테스트 결과: ✅ **BUILD SUCCESSFUL** (GHG 17건 포함 전체 테스트 통과)

---

### 2026-05-20 | Phase 3-B 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| 증빙 파일 업로드 — `DigestInputStream` 단일 I/O | ✅ | `CountingDigestInputStream` — SHA-256 + 파일 크기 동시 계산 |
| 경로 순회 방어 — `resolveContained()` | ✅ | `normalize() + startsWith()` — `../../../etc/passwd` 거부 |
| 확장자 허용 목록 | ✅ | `pdf, xlsx, xls, csv, png, jpg, jpeg` 7종만 허용 |
| `UnitConverter` — BigDecimal 전용, scale=6 HALF_UP | ✅ | 12종 양방향 변환, `double` 사용 없음 |
| 활동 데이터 — 원 단위 + 변환 단위 병행 저장 | ✅ | `ActivityData.create()` → `UnitConverter.standardUnitFor()` + `convert()` 자동 적용 |
| Formula DSL — DoS 방어 상수 | ✅ | `FormulaConstants.MAX_EXPRESSION_LENGTH=1000`, `MAX_EVAL_DEPTH=50` |
| Formula `test_cases` 게이트 | ✅ | `test_cases` 비어 있으면 `FormulaValidationException` — 활성화 차단 |
| YAML 산식 3종 — test_cases 통과 | ✅ | EM-S1-FUEL, EM-S2-LB, EM-S2-MB 각 2~3건 케이스 모두 통과 |
| ESG 지표 초기 데이터 | ✅ | V11: 8개 KSSB2 지표 (S-001~003, G-001~002, E-001~003) |
| Spring Modulith 경계 — `ghg.internal` 외부 노출 차단 | ✅ | `EvidenceFileRepository`, `EvidenceFileJpaEntity` → `public`으로 변경하여 `ghg.infra` 내부 접근 허용 |
| ModularityTest | ✅ PASS | 모듈 경계 위반 없음 |
| 전체 테스트 | ✅ **94 tests, 0 failures** | Phase 0~3-B 누적 |

**Phase 3-B 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `entity` 모듈 — Phase 2 부채: `DefaultEntityManagementService` Mapper 미도입 | **P1** | `LegalEntityMapper`, `EntityRelationshipMapper` 생성 후 서비스 교체 — `JpaEntity.builder()` 직접 호출 제거 |
| `EvidenceFileRepository`, `EvidenceFileJpaEntity` — package-private 접근 오류 | **필수** | 두 클래스 `public`으로 변경 (Spring Modulith 모듈 내 cross-package 접근 허용) |

---

### 2026-05-20 | Phase 3 + 3-B 종합 재검토 수정

> 검토 결과: 13건 발견 (P0: 3건, P1: 3건, P2: 4건, P3: 1건, 정보성: 2건)

**P0 수정 (즉시)**:

| 발견 사항 | 수정 내용 |
|---|---|
| `activity_data` 테이블 — RLS 정책 미적용 | `V12__activity_data_rls.sql` 생성 — `ENABLE ROW LEVEL SECURITY` + tenant_isolation 정책 |
| `evidence_files` 테이블 — RLS 정책 미적용 | `V13__evidence_files_rls.sql` 생성 — `ENABLE ROW LEVEL SECURITY` + tenant_isolation 정책 |
| `ActivityDataResponse`, `EmissionRecordResponse` — `tenantId` 필드 노출 | 두 응답 DTO에서 `tenantId` 제거, `DefaultGhgService` 매핑 메서드 수정 |

**P1 수정 (재현성 보호)**:

| 발견 사항 | 수정 내용 |
|---|---|
| `EmissionFactorLoader.upsert()` — `entity.updateValue()` UPDATE 호출로 재현성 파괴 | UPDATE 금지. 기존 계수 `deactivate(today-1)` + 새 계수 INSERT (effective_from=today). `V14__fix_ef_unique_constraint.sql`로 UNIQUE 제약도 `effective_from` 포함으로 교체 |
| `EmissionRecordRepository` — `findById()` 미노출 | `Optional<EmissionRecordJpaEntity> findById(UUID id)` 추가 |
| `SimpleExpressionEvaluator` — `double` 사용 (test_cases 전용이므로 정밀도 우려) | 클래스 문서 주석에 "test_cases 검증 전용 — 실제 배출량 계산은 BigDecimal" 명시 |

**P2 수정 (성능)**:

| 발견 사항 | 수정 내용 |
|---|---|
| `calculateEmissions()` 루프 내 N회 배출계수 DB 조회 | `Map<String, EmissionFactor> factorCache` 도입 — 동일 카테고리 2번째 조회부터 캐시 히트 |
| `deriveScopeFromCategory()` — SCOPE2_LB vs SCOPE2_MB 구분 불가 | `category.endsWith("_MB")` 선 체크 후 `_LB` 판별 로직 추가 |

**P3 수정 (코드 품질)**:

| 발견 사항 | 수정 내용 |
|---|---|
| `ActivityDataJpaEntity` — 상태 전이 메서드 부재 | `submit(actorId)`, `approve(actorId)`, `reject(actorId, reason)` 명시적 전이 메서드 추가 (01-domain-architecture.md 승인 상태 기계 원칙) |

전체 테스트 결과: ✅ **BUILD SUCCESSFUL** (94 tests, 0 failures)

---

### Phase 4: 다법인 연결 집계 엔진 리뷰 (2026-05-20)

**구현 범위**
- `ConsolidationEngine` 도메인 서비스 (Equity / Operational Control Method)
- `EntityRelationshipGraph`를 DAG로 표현, effectiveOwnershipRatio 경로 곱으로 이중 계상 제거
- `ConsolidatedEmissionRecord` + `ConsolidatedEmissionContribution` Append-only 저장 (P1 재현성)
- `ConsolidationService` 인터페이스 + `DefaultConsolidationService` 구현
- `GhgController`에 연결 집계 엔드포인트 추가 (POST/GET `/consolidations`)
- Spring Modulith 경계: `entity.api/`에 `@NamedInterface("api")` 선언

**발견 및 수정 이슈**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P1 | `ConsolidationMethod`, `EntityRelationship`, `EntityRelationshipGraph`가 `entity.domain/`에 위치 → Spring Modulith 경계 위반 | 세 타입을 `entity.api/`로 이동 + `@NamedInterface("api")` 선언 |
| P2 | `EmissionFactorLoaderTest.cleanup()`이 `emission_records`를 먼저 삭제하지 않아 FK 위반 발생 | cleanup에 `consolidated_*`, `emission_records`, `activity_data` 삭제 순서 추가 |
| P3 | `ConsolidationServiceIntegrationTest`: `tenants` 테이블에 테스트 테넌트(`000...003`) 미등록 → FK 위반 | `@BeforeEach`에 `INSERT INTO tenants ... ON CONFLICT DO NOTHING` 추가 |
| P3 | 감사로그 테스트: Outbox 패턴 고려 없이 직접 `audit_logs` 카운트 | `outboxProcessingService.processNow()` 호출 후 검증 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (105 tests, 0 failures)
- 도메인 단위 테스트 7건 (ConsolidationEngineTest)
- 통합 테스트 5건 (ConsolidationServiceIntegrationTest)
- ModularityTest 통과 (entity.api NamedInterface 경계 준수)

---

## 3. 공통 이슈 트래킹

> 같은 이슈가 3회 이상 반복되면 체크리스트에 항목 추가

| 이슈 패턴 | 발생 횟수 | 대응 |
|---|---|---|
| 테스트에서 `@WithMockUser` 사용 시 typed `@AuthenticationPrincipal` NPE | 1 | `WithMockJwtUser` 커스텀 어노테이션 패턴 수립 |

---

## 4. Phase별 리뷰 포커스

| Phase | 중점 리뷰 항목 |
|---|---|
| Phase 0 | 모듈 패키지 구조 (중간 `module/` 디렉터리 없음), CI 파이프라인 정확성, Flyway 체크섬 불변 확인 |
| Phase 1 | Domain≠Entity 원칙, RLS 정책 적용, TenantContextInterceptor `SET LOCAL` 검증, 크로스 테넌트 이중 방어 |
| Phase 2 | @Auditable AOP 커버리지, Hash Chain PESSIMISTIC_WRITE, Canonical JSON 단일 직렬화 경로 |
| Phase 3 | YAML 로더 멱등성, 배출계수 계산 `BigDecimal` 전용(float/double 금지), `reporting_year` SQL 예약어 방지, `factorAt` 재현성 테스트 |
| Phase 4 | 연결 집계 이중 계상 제거 알고리즘 |
| Phase 5 | Scope 3 95% 임계값 계산 로직, 데이터 품질 점수 |
| Phase 6 | 공급업체 데이터 격리, Webhook 시그니처 검증 |
| Phase 7 | KSSB 2 항목 완전성 (미구현 항목 없음), YoY 계산 |
| Phase 8 | 스냅샷 불변성, VERIFIER RLS 격리 |
| Phase 9~11 | XSS 방어, RBAC 메뉴 분기, 접근성 |
| Phase 12 | 성능 벤치마크, OWASP 결과 해소 |
