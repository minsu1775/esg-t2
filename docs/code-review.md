# ESG 공시지원 시스템 esg-t2 — 코드 리뷰 체크리스트 & 이력 (Code Review)

> **목적**: 코드 리뷰에서 반복적으로 확인해야 할 항목을 정의하고, 주요 리뷰 결과를 기록한다.  
> **갱신**: 새로운 공통 이슈 발견 시 체크리스트에 항목 추가

---

## 1. 필수 체크리스트 (PR 생성 전 자가 점검)

### 1.1 도메인 설계

- [ ] 서비스에서 `JpaEntity.builder()...build()` 직접 호출 없음 (도메인 팩토리만 허용)
- [ ] 도메인 객체는 불변(record 또는 final 필드) 또는 명시적 변경 메서드만 존재
- [ ] `DomainObject.create(cmd)` 팩토리 메서드에 유효성 검사 포함
- [ ] `domain/` 패키지 파일에 Spring·JPA 임포트(`org.springframework.*`, `jakarta.persistence.*`) 없음 (BUG-P6-04)

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
- [ ] 역할 기반 `@PreAuthorize` 적용 (인가 없는 엔드포인트 없음; 면제 시 사유 주석 필수)
- [ ] 크로스 테넌트 데이터 접근 방지: RLS 정책 + 애플리케이션 레벨 이중 방어
- [ ] `permitAll` 경로 추가 시 `TenantContextInterceptor`에서 RLS 컨텍스트 설정 여부 확인 (BUG-P6-02)
- [ ] 새 데이터 수집 서비스(CSV·Webhook 등): 서비스 진입 직후 `entityId` 테넌트 소속 검증 포함 (BUG-P6-03)

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

**발견 및 수정 이슈 (구현 중)**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P1 | `ConsolidationMethod`, `EntityRelationship`, `EntityRelationshipGraph`가 `entity.domain/`에 위치 → Spring Modulith 경계 위반 | 세 타입을 `entity.api/`로 이동 + `@NamedInterface("api")` 선언 |
| P2 | `EmissionFactorLoaderTest.cleanup()`이 `emission_records`를 먼저 삭제하지 않아 FK 위반 발생 | cleanup에 `consolidated_*`, `emission_records`, `activity_data` 삭제 순서 추가 |
| P3 | `ConsolidationServiceIntegrationTest`: `tenants` 테이블에 테스트 테넌트(`000...003`) 미등록 → FK 위반 | `@BeforeEach`에 `INSERT INTO tenants ... ON CONFLICT DO NOTHING` 추가 |
| P3 | 감사로그 테스트: Outbox 패턴 고려 없이 직접 `audit_logs` 카운트 | `outboxProcessingService.processNow()` 호출 후 검증 |

---

### Phase 4 재검토: 7개 영역별 심층 리뷰 (2026-05-20)

> 영역별 검토: ① 비즈니스 로직(GHG Protocol) ② 보안 ③ 성능 ④ API 설계 ⑤ 테스트 커버리지 ⑥ OpenAPI ⑦ DDL 품질

**① 비즈니스 로직 — GHG Protocol 정확성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `consolidateOperationalControl()` — 다단계 구조에서 "실질 소유율(경로 곱) > 50%" 방식 사용. GHG Protocol은 "직접 지배 체인 각 링크 > 50%"로 판별해야 함. A→B(60%)→C(70%) 체인에서 C의 실질 소유율 0.42로 제외했으나, GHG Protocol 기준 C도 포함되어야 함 | `EntityRelationshipGraph.hasDirectControlChain(from, to, threshold)` 추가. `ConsolidationEngine.consolidateOperationalControl()` 해당 메서드로 교체. 통합 테스트 합계 수정: 519.2 → 778.8 (C 포함) |
| P3 | `ConsolidationEngine` 지역 변수명 `directRatio`가 `effectiveOwnershipRatio(경로 곱)`를 담아 오해 유발 | 코드 주석으로 방법론 차이 명시 |

**② 보안 — 크로스 테넌트 방어**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P0** | `DefaultConsolidationService.consolidate()` — `rootEntityId`의 테넌트 소속 검증 없음. RLS만으로는 방어 불충분 (03-security.md: 애플리케이션 레벨 이중 검증 필수) | `EntityManagementService.findById(tenantId, entityId)` 추가. 서비스 진입 직후 검증 → 불일치 시 `RESOURCE_NOT_FOUND(404)` |

**③ 성능 — N+1 쿼리 및 배치 처리**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P2 | `buildDirectEmissions()` — entityId당 개별 DB 조회 (10법인 = 10쿼리) | `EmissionRecordRepository.findByTenantIdAndEntityIdInAndReportingYear()` IN 쿼리 추가 후 일괄 조회. Java에서 entityId별 합산 |
| P2 | `findConsolidations()` — 각 집계 레코드당 기여분 별도 조회 (N+1) | `ConsolidatedEmissionContributionRepository.findByConsolidatedRecordIdIn()` 추가. recordIds IN 쿼리 후 Java groupingBy |
| P2 | `persistContributions()` — 기여분 개별 `save()` 반복 | `ConsolidatedEmissionContributionRepository.saveAll()` 추가. 리스트 빌드 후 단일 배치 INSERT |

**④ API 설계 — 타입 안전성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `ConsolidationService.consolidate()` `method` 파라미터가 `String` → 런타임 오류 가능 | 인터페이스·구현체·컨트롤러 모두 `ConsolidationMethod` enum으로 교체. `parseMethod()` 헬퍼 제거 |
| P3 | `GlobalExceptionHandler` — `MethodArgumentTypeMismatchException` 핸들러 없음 → enum 변환 실패 시 500 반환 | `MethodArgumentTypeMismatchException` 핸들러 추가 → 400 응답 |
| P3 | `GhgController` 연결 집계 엔드포인트 — `@Parameter` 어노테이션 없음 (OpenAPI 미문서화) | `reportingYear`, `method` 파라미터에 `@Parameter(description, example)` 추가 |

**⑤ 테스트 커버리지**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P2 | Operational Control 3단계 체인 케이스 없음 (2단계만 테스트) | `ConsolidationEngineTest`: `Operational_Control_3단계_체인_모든_링크_지배_시_하위도_포함`, `Operational_Control_3단계_중간_링크_비지배면_하위_전체_제외` 2건 추가 |

**⑥ OpenAPI 문서화**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `ConsolidationResponse`, `ConsolidationItemResponse` 필드 `@Schema` 없음 | 두 record에 `@Schema(description, example)` 전수 적용 |
| P3 | `GhgApi.java` 주석이 `ConsolidationService` 미언급 | ConsolidationService 포함으로 주석 업데이트 |

**⑦ DDL 품질**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `consolidated_emission_contributions.ownership_ratio` — 0~1 범위 CHECK 제약 없음 | `V17__add_contribution_constraints.sql`: `CHECK (ownership_ratio IS NULL OR (ownership_ratio > 0 AND ownership_ratio <= 1))` 추가 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (107 tests, 0 failures)
- 도메인 단위 테스트 9건 (ConsolidationEngineTest +2)
- 통합 테스트 5건 (ConsolidationServiceIntegrationTest)
- ModularityTest 통과

---

### Phase 5: Scope 3 계산 엔진 리뷰 (2026-05-20)

> 구현 범위: Cat.1(지출기반), Cat.2(자본재), Cat.11(판매제품 사용), Scope3CoverageCalculator, V18 마이그레이션, Scope3Service + GhgController 5개 엔드포인트

**① 비즈니스 로직 — GHG Protocol 정확성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| OK | Cat.1 `spendAmount × factorValue` 정확도 | `EmissionCalculator.computeEmission()` 공유 — BigDecimal HALF_UP scale 6 유지 |
| OK | Cat.11 생애주기 귀속 `(q × ef) / lifetimeYears` | `Scope3Cat11Calculator.computeAnnualEmission()`: lifetimeYears null·0 이하 시 `IllegalArgumentException` |
| OK | 95% 임계값 계산 `included/(included+excluded) ≥ 0.95` | `Scope3CoverageCalculator`: totalEstimated=0 시 100% 처리, 제외 카테고리 사유 필수 |

**② 보안 — 크로스 테넌트 방어**

| 심각도 | 항목 | 상태 |
|---|---|---|
| P0 | `DefaultScope3Service` 모든 조회에 `tenantId` 파라미터 전달 → JPA WHERE절 필터 | ✅ 적용됨 |
| P0 | `scope3_coverage_reports` RLS 정책 (PostgreSQL 전용 V18) | ✅ `migration-pg/V18__scope3_rls.sql` |

**③ 인프라 — 컬럼 매핑 이슈**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P2** | `meets95PctThreshold` → Hibernate 변환 `meets95_pct_threshold` vs DB 컬럼 `meets_95pct_threshold` 불일치 | `@Column(name = "meets_95pct_threshold")` 명시적 매핑 추가 |

**④ 테스트 격리 이슈**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P2 | `@BeforeEach`의 `DELETE FROM emission_factors` — 다른 테스트 클래스의 `emission_records` FK 참조로 인한 PSQLException | `DELETE FROM emission_factors` 제거, `loadFile()` upsert 방식으로 전환. SCOPE3 카테고리는 다른 테스트 YAML과 category 겹침 없음 |
| P2 | `@Auditable` AOP — `SecurityContextHolder`에 `JwtAuthentication` 없으면 outbox 이벤트 미생성 | `@BeforeEach`에 `SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(...))` 추가 |

**⑤ API 설계 — 타입 오류**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `CreateEntityRequest` 생성자 — `LegalEntityType` 파라미터에 `.name()` String 전달 | 통합 테스트에서 `LegalEntityType.PARENT` enum 직접 전달로 수정 |
| P3 | `EntityManagementService.createEntity()` 메서드명 불일치 — 실제는 `create()` | 통합 테스트에서 `create(tenantId, request)` 2인자 시그니처로 수정 |

**⑥ 감사 컬럼명 확인**

| 심각도 | 항목 | 상태 |
|---|---|---|
| P3 | `audit_logs` 컬럼은 `event_type` (plan 예시에서 `action`으로 잘못 참조) | 통합 테스트 SQL 쿼리 `WHERE event_type = '...'`로 수정 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (129 tests, 0 failures)
- 도메인 단위 테스트 17건 (Scope3CalculatorTest: Cat1×6, Cat2×2, Cat11×5, Coverage×4)
- 통합 테스트 5건 (Scope3IntegrationTest: Cat1/Cat2/Cat11 + 커버리지 생성·미달)
- ModularityTest 통과

---

### Phase 5 재검토: 구현 흐름 전수 점검 (2026-05-20)

> 관점: ① 비즈니스 로직 연결 완결성 ② 유효성 검사 정확성 ③ 도메인 아키텍처 일관성 ④ 재현성

**① 비즈니스 로직 연결 완결성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P2** | T-5-03 "Cat.1 데이터 품질 자동 부여" — `deriveDataQuality()` 단위 테스트만 통과, `ActivityData.create()` 경로에서 미호출. Cat.1 등록 시 항상 `AVERAGE_DATA` 저장 | `ActivityData.create()`: `"SCOPE3_CAT1"` 카테고리 감지 후 `Scope3Cat1Calculator.deriveDataQuality(dataSource)` 자동 적용. 회귀 단위 테스트 3건 추가 (T-5R-01) |
| P3 | `DefaultScope3Service.calculateScope3()` — `Scope3Cat1/2Calculator.computeEmission()` 미경유, `EmissionCalculator` 직접 호출. 계산기 클래스 존재 목적 반감 | `computeEmission()` private 헬퍼를 `switch(scope3CategoryNum)`으로 교체, 카테고리별 계산기 경유 (T-5R-04) |

**② 유효성 검사 정확성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `Scope3CoverageRequest.@NotNull int reportingYear` — primitive `int`에 `@NotNull` 효과 없음 (컴파일만 통과) | `@Min(2020) @Max(2030)` 으로 교체 (T-5R-02) |

**③ 도메인 아키텍처 — 재현성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | `Scope3CoverageCalculator`: `HashMap.keySet()` → `includedCategories` 순서 비결정적. JSON `[2,1]` vs `[1,2]` 불일치 | `.stream().sorted().toList()` 오름차순 정렬 보장 (T-5R-03) |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (132 tests, 0 failures)
- 신규 단위 테스트 3건 추가 (ActivityData Cat.1 데이터품질 자동 결정 × 3)
- 전체 132건 통과, ModularityTest 통과

---

### Phase 6A: CSV 업로드 + Webhook 데이터 수집 파이프라인 리뷰 (2026-05-20)

> 구현 범위: T-6-01~06, T-6-12~15 / IntakeController + DefaultIntakeService + ActivityDataRowImporter + CsvActivityDataParser

**① 트랜잭션 설계 — REQUIRES_NEW 행별 격리**

| 심각도 | 항목 | 내용 |
|---|---|---|
| - | `DefaultIntakeService` — `@Transactional` + `@Auditable` 외부 트랜잭션 보유 | `@Auditable` AOP가 outbox 이벤트를 커밋하기 위해 외부 트랜잭션이 필요. `uploadCsv()` / `receiveWebhook()` 모두 `@Transactional` 유지. 행별 처리는 `ActivityDataRowImporter`의 `REQUIRES_NEW`에 위임 — 2계층 트랜잭션 구조 의도적 설계 |
| - | `ActivityDataRowImporter.importRow()` — `Propagation.REQUIRES_NEW` | 중간 행 오류가 전체 롤백을 유발하지 않음. 성공한 행은 즉시 커밋되어 재업로드 시 중복으로 감지 |

**② 보안 — Webhook HMAC-SHA256 검증**

| 심각도 | 항목 | 내용 |
|---|---|---|
| - | `IntakeController.isValidSignature()` — `MessageDigest.isEqual()` 상수 시간 비교 사용 | 타이밍 공격 방지. `String.equals()` 사용 금지 원칙 준수 |
| - | Webhook 경로 `permitAll` + system actor SecurityContext 주입 | JWT 없는 Webhook에도 `@Auditable` 감사 로그 기록 가능. `SYSTEM_WEBHOOK_ACTOR = 00000000-...0099` well-known ID 사용 |
| - | `/api/v1/intake/tenants/*/webhook` — Spring Security `permitAll` 등록 | HMAC이 인증 역할을 대체하므로 JWT 필터 우회 필요 |

**③ 구현 중 발견·수정 사항**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P2 | `TestRestTemplate` Spring Boot 4에서 제거 | 표준 `RestTemplate` + `DefaultResponseErrorHandler` 비활성화로 대체. 4xx 응답을 예외 없이 `ResponseEntity`로 수신 |
| P3 | `GlobalExceptionHandler` — `WEBHOOK_SIGNATURE_INVALID` 코드가 `default → 500`으로 빠짐 | `case WEBHOOK_SIGNATURE_INVALID -> HttpStatus.UNAUTHORIZED` 추가. `CSV_PARSE_FAILED → BAD_REQUEST` 함께 추가 |

**④ 아키텍처 테스트**

| 항목 | 결과 |
|---|---|
| `@Async` + `@Transactional` 동시 부착 방지 (`AsyncTransactionalArchTest`) | ✅ PASS — `ClassPathScanningCandidateComponentProvider` 사용, 외부 라이브러리 불필요 |
| Spring Modulith `ModularityTest` | ✅ PASS |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (전체 테스트 통과)
- 신규 단위 테스트: `CsvActivityDataParserTest` 6건
- 신규 통합 테스트: `IntakeIntegrationTest` 6건 (CSV 100행 멱등성, REQUIRES_NEW 격리, 중복 WARN, Webhook HMAC 검증)
- 신규 아키텍처 테스트: `AsyncTransactionalArchTest` 1건
- ModularityTest 통과

---

### Phase 6A 재검토: 5개 영역 심층 리뷰 (2026-05-20)

> 관점: ① 예외 처리 HTTP 상태 코드 ② RLS 컨텍스트 완결성 ③ 크로스 테넌트 방어 ④ 아키텍처 순수성 ⑤ 입력 유효성

**① 예외 처리 — HTTP 상태 코드 정확성**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `CsvActivityDataParser.parse()` 예외(`IllegalArgumentException`)가 `GlobalExceptionHandler`의 `Exception.class` → **500** 반환 | `DefaultIntakeService.uploadCsv()`에서 `IllegalArgumentException` → `EsgException(CSV_PARSE_FAILED)` 래핑. `IOException`도 동일하게 처리. → **400** 반환 |
| - | `CsvActivityDataParser`가 `Resource` 대신 `InputStream` 수용 | Spring 의존 제거 — 동시에 발생한 아키텍처 위반 수정 |

**② RLS 컨텍스트 완결성 — Webhook 경로 누락**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | JWT 없는 Webhook 요청: `TenantContextInterceptor`의 `auth == null` 조건으로 즉시 반환 → `app.current_tenant_id` 미설정. 운영 환경 RLS 무력화 | `TenantContextInterceptor`에 `WEBHOOK_TENANT_PATTERN` 추가. URL에서 tenantId 추출 후 `set_config` 호출 |
| - | 테스트 환경은 `db/migration-pg` 미실행(RLS 미활성) → 테스트에서 감지 불가 | **이 유형의 버그는 통합 테스트에서 발견 불가** — 운영 배포 전 RLS 정책 점검 필수 |

**③ 크로스 테넌트 방어 — entityId 소속 검증**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `DefaultIntakeService.uploadCsv(entityId)` — entityId가 tenantId에 속하는지 검증 없음 | `EntityManagementService.findById(tenantId, entityId)` 추가. 불일치 시 `EsgException(VALIDATION_FAILED)` |
| **P1** | `receiveWebhook(items)` — 각 `item.entityId()` 소속 검증 없음 | unique entityId 일괄 검증 후 미소속 존재 시 일괄 거부 |
| - | Phase 4에서 `DefaultConsolidationService`에 동일 패턴 적용됐으나 Intake 경로 누락 | 패턴 불일치 — 신규 서비스 구현 체크리스트에 "entityId 소속 검증" 항목 추가 필요 |

**④ 아키텍처 순수성 — domain 패키지 Spring 의존**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P2 | `CsvActivityDataParser`(domain/)가 `import org.springframework.core.io.Resource` — 01-domain-architecture.md "Domain = 순수 Java" 위반 | 시그니처 변경: `parse(Resource)` → `parse(InputStream)`. 호출 측에서 `csvFile.getInputStream()` 전달 |

**⑤ 입력 유효성 — 음수 quantity**

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| P3 | CSV/Webhook에서 음수 quantity 입력 시 ERROR 없이 DB 저장 | `ActivityDataRowImporter.importRow()`에 `row.quantity().signum() <= 0` 검증 추가 → ERROR 반환 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (147 tests, 0 failures)
- `IntakeIntegrationTest` +2건 추가: `음수_quantity_행은_ERROR_반환`, `존재하지_않는_entityId로_CSV_업로드_시_예외`
- `CsvActivityDataParserTest` InputStream 시그니처 반영

---

### Phase 6B: 공급업체 포털 리뷰 (2026-05-22)

> 구현 범위: T-6-07~15 / supply 모듈 (SupplierController, SupplierService, DefaultSupplierService)

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `@AuthenticationPrincipal JwtAuthentication auth` — `getPrincipal()`이 UUID를 반환하므로 캐스트 불가 → auth가 null | `Authentication authentication` 파라미터 + `(JwtAuthentication) authentication` 명시적 캐스트로 전면 교체 (BUG-P6B-01) |
| P2 | Spring Modulith `ghg.api` `@NamedInterface` 누락 → ModularityTest 실패 | `ghg/api/package-info.java` 생성: `@NamedInterface("api")` (BUG-P6B-02) |
| P2 | `SupplyTestConfig` — `StubEmailGateway` + `EmailGateway` 중복 `@Primary` 등록 | 단일 빈으로 통합 (BUG-P6B-03) |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (155 tests, 0 failures)

---

### Phase 6-B: 정정·재공시 워크플로우 & Formula DSL 배포 리뷰 (2026-05-22)

> 구현 범위: T-6B-01~09 / 정정 워크플로우, ActivityDataCorrectedEvent, Formula 버전 관리

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `@EventListener` + `@Transactional(REQUIRED)` → 배출계수 미존재 시 정정 트랜잭션 rollback-only 마킹 | `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` 독립 트랜잭션으로 변경 (BUG-P6B-04) |
| P2 | `shared.event` 패키지 `@NamedInterface` 누락 → ModularityTest 위반 | `shared/event/package-info.java` 생성: `@NamedInterface("events")` (BUG-P6B-05) |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (197 tests, 0 failures)

---

### Phase 7: 공시 보고서 생성 리뷰 (2026-05-22)

> 구현 범위: T-7-01~09 / rpt 모듈 (ReportController, DefaultReportService, PDF 생성)

| 항목 | 결과 | 비고 |
|---|---|---|
| 보고서 상태 기계 (DRAFT→SUBMITTED→APPROVED/REJECTED) | ✅ | 명시적 전이 메서드만 노출, `setStatus()` 없음 |
| `isApproved()` — vw 모듈 게이트 | ✅ | `DefaultSnapshotService.createSnapshot()`에서 호출 |
| `rpt.api @NamedInterface` | ⚠️ 누락 → 즉시 수정 | Phase 8에서 vw→rpt 참조 시 ModularityTest 실패로 발견. `rpt/api/package-info.java` 생성 (BUG-P8-02) |
| `@Auditable` — REPORT_CREATED, SUBMITTED, APPROVED, REJECTED | ✅ | 4개 변경 메서드 모두 부착 |
| PDF 매직 바이트 검증 (`%PDF`) | ✅ | `ReportIntegrationTest.PDF_생성_바이트_배열_반환()` |
| `@Auditable` 통합 테스트 검증 | ⚠️ 누락 → 후속 리뷰에서 추가 | `보고서_생성_시_감사로그가_기록된다()` 추가 완료 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (197 tests, 0 failures)

---

### Phase 8: 외부 검증 워크스페이스 리뷰 (2026-05-22)

> 구현 범위: T-8-01~10 / vw 모듈 (SnapshotService, VwController, VERIFIER 격리)

| 항목 | 결과 | 비고 |
|---|---|---|
| 스냅샷 불변성 — REVOKE UPDATE/DELETE + BEFORE 트리거 | ✅ | `vw_pg_rls.sql`: DB 레벨 이중 방어 |
| VERIFIER RLS 격리 — `app.verifier_snapshot_id` SET LOCAL | ✅ | `JwtAuthentication.getSnapshotId()` → `TenantContextInterceptor` |
| SHA-256 해시 64자 hex (재현성) | ✅ | `LinkedHashMap` 필드 순서 고정 → 동일 JSON → 동일 해시 |
| 서명 분리 테이블 `verification_signatures` | ✅ | 스냅샷 본체 완전 불변 유지, UNIQUE(snapshot_id) 제약 |
| `@Auditable` — SNAPSHOT_CREATED, COMMENT_ADDED, SIGNED | ✅ | 3개 변경 메서드 모두 부착 |
| `ObjectMapper` 빈 미등록 | ⚠️ 발견 → 즉시 수정 | static `SNAPSHOT_MAPPER` 상수로 교체 (BUG-P8-01) |
| `@Auditable` 통합 테스트 검증 | ⚠️ 누락 → 후속 리뷰에서 추가 | `스냅샷_생성_시_감사로그가_기록된다()` 추가 완료 |

**Phase 0~8 전체 리뷰 추가 개선 (2026-05-22)**:

| 심각도 | 항목 | 수정 내용 |
|---|---|---|
| **P1** | `EntityController`, `IntakeController` — `@AuthenticationPrincipal JwtAuthentication` 패턴 잔존 (BUG-P6B-01 미수정) | `Authentication authentication` + `(JwtAuthentication)` 캐스팅으로 전면 교체 |
| P2 | `ActuatorEndpointTest` — TestContainers 미사용 + MailHealthIndicator 503 | `AbstractIntegrationTest` 확장 + `management.health.mail.enabled=false` 추가 (BUG-P8-03) |
| P2 | `ReportIntegrationTest`, `SnapshotIntegrationTest` — `@Auditable` 검증 누락 | `보고서_생성_시_감사로그가_기록된다()`, `스냅샷_생성_시_감사로그가_기록된다()` 추가 |

**테스트 결과**: ✅ **BUILD SUCCESSFUL** (205 tests, 0 failures)

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
| Phase 4 | Operational Control GHG Protocol 준수 (직접 지배 체인), 크로스 테넌트 rootEntityId 검증, N+1 쿼리 |
| Phase 5 | ✅ Scope 3 95% 임계값 계산 로직, meets_95pct_threshold 컬럼 매핑, 테스트 격리(@BeforeEach FK), AuditAspect JWT 컨텍스트 |
| Phase 6 | ✅ CSV 오류 HTTP 상태 코드(400), RLS 컨텍스트 완결성(permitAll 경로), entityId 테넌트 소속 검증, quantity 음수 방어, @PreAuthorize 면제 문서화 |
| Phase 7 | KSSB 2 항목 완전성 (미구현 항목 없음), YoY 계산 |
| Phase 8 | 스냅샷 불변성, VERIFIER RLS 격리 |
| Phase 9~11 | XSS 방어, RBAC 메뉴 분기, 접근성 |
| Phase 12 | 성능 벤치마크, OWASP 결과 해소 |
