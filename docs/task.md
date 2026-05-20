# ESG 공시지원 시스템 esg-t2 — 태스크 목록 (Task)

> **버전**: 1.0  
> **작성일**: 2026-05-18  
> **레퍼런스**: plan.md v1.0  
> **상태 범례**: `TODO` | `IN_PROGRESS` | `DONE` | `BLOCKED` | `SKIP`

---

## Phase 0: 프로젝트 셋업 & 인프라 기반

| ID | 태스크 | 상태 | 담당 | 비고 |
|---|---|---|---|---|
| T-0-01 | Spring Boot 4 + Java 25 Gradle 프로젝트 생성 (루트+frontend/) | DONE | | Spring Initializr로 wrapper 생성 후 build.gradle.kts 교체 |
| T-0-02 | Spring Boot 4.0.x + Java 25 설정 | DONE | | build.gradle.kts 의존성 |
| T-0-03 | Spring Modulith 의존성 + 모듈 패키지 뼈대 생성 | DONE | | ghg, entity, vw, rpt, supply, audit, shared |
| T-0-04 | PostgreSQL 18 Docker Compose 설정 | DONE | | docker-compose.yml |
| T-0-05 | Redis Docker Compose 설정 | DONE | | JWT 블랙리스트·캐시 |
| T-0-06 | Flyway 설정 + V1__initial_schema.sql | DONE | | tenants, disclosure_schedules, event_publication |
| T-0-07 | AbstractIntegrationTest (Testcontainers PostgreSQL) | DONE | | 모든 통합 테스트의 부모 클래스 |
| T-0-08 | ModularityTest 초기 설정 | DONE | | `./gradlew test --tests "*ModularityTest"` |
| T-0-09 | GitHub Actions CI 파이프라인 | DONE | | test → modularity-check → build |
| T-0-10 | OpenTelemetry 설정 (OTLP exporter) | SKIP | | OTLP exporter는 Phase 12 관측성 구축 단계로 이동. Prometheus(T-0-11)로 대체 |
| T-0-11 | Prometheus 메트릭 엔드포인트 노출 | DONE | | /actuator/prometheus — ActuatorEndpointTest 통과 |
| T-0-12 | Next.js 프로젝트 초기화 (latest) | DONE | | Next.js 16.2.6, TypeScript strict, Tailwind 4.x, App Router, frontend/src/ 구조 |
| T-0-13 | disclosure_schedule 초기 데이터 마이그레이션 | DONE | | V2__disclosure_schedule_seed.sql — KSSB 1/2, ISSB_S2, CSRD |
| T-0-14 | **[예방]** Flyway locations 분리 — db/migration + db/migration-pg | DONE | | esg-t1 BUG-P5-09 교훈 |
| T-0-15 | **[예방]** application-test.yml: `scheduler.enabled=false` 설정 | DONE | | 스케줄러 테스트 격리 |
| T-0-16 | **[예방]** AbstractIntegrationTest: `static { POSTGRES.start(); }` 패턴 | DONE | | @DynamicPropertySource 전 컨테이너 시작 보장 |

---

## Phase 1: 법인·테넌트 관리 (entity 모듈)

### DB

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-1-01 | V3__entity_tables.sql — legal_entities, entity_relationships | DONE | 공통 DDL만. RLS 정책은 db/migration-pg/V3__rls_policies.sql 별도 분리 |
| T-1-02 | V4__auth_tables.sql — users, user_roles | DONE | |

### 도메인

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-1-03 | `test:` LegalEntity 생성 실패 케이스 (필수 필드 누락, 국가코드 유효성) | DONE | |
| T-1-04 | `feat:` LegalEntity 도메인 + 팩토리 메서드 | DONE | `LegalEntity.create(cmd)` |
| T-1-05 | `test:` EntityRelationship 지분율 범위 유효성 (0~1) | DONE | |
| T-1-06 | `feat:` EntityRelationship 도메인 + 순환 참조 방지 | DONE | DAG 사이클 탐지 |
| T-1-07 | `test:` EntityRelationshipGraph 트리 탐색 | DONE | 부모→자식 경로 순회 |
| T-1-08 | `feat:` EntityRelationshipGraph 구현 | DONE | |

### 보안·API

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-1-09 | `test:` TENANT_ADMIN만 법인 생성 가능 (보안 테스트) | DONE | |
| T-1-10 | `feat:` Spring Security RBAC 설정 + JwtAuthenticationFilter | DONE | 6개 역할, HMAC-SHA256 자체 JWT |
| T-1-11 | **[필수]** `feat:` TenantContextInterceptor — 요청마다 `SET LOCAL app.current_tenant_id` | DONE | RLS 전제 조건 — SecurityFilter 이후, API 이전에 위치 |
| T-1-12 | `feat:` JWT 발급 (POST /api/v1/auth/login) | DONE | Access 15분, Refresh 7일 |
| T-1-13 | `feat:` JWT 갱신 (POST /api/v1/auth/refresh) | DONE | |
| T-1-14 | `feat:` 로그아웃 — Redis 블랙리스트 (POST /api/v1/auth/logout) | DONE | |
| T-1-15 | `feat:` 법인 등록 API (POST /api/v1/entities) | DONE | @Auditable 어노테이션 부착 (AOP는 Phase 2) |
| T-1-16 | `feat:` 법인 목록 API (GET /api/v1/entities) | DONE | 트리 구조 응답 |
| T-1-17 | `feat:` 관계 설정 API (PUT /api/v1/entities/{id}/relationships) | DONE | |
| T-1-18 | **[예방]** `test:` `AccessDeniedException` → 403 응답 확인 (미처리 시 500) | DONE | esg-t1 L-0-10 교훈 |
| T-1-19 | **[예방]** `test:` `ObjectOptimisticLockingFailureException` → 409 응답 확인 | DONE | |
| T-1-20 | **[예방]** `test:` ERROR severity 검증 실패 → `create()` 차단 확인 (검증 우선 원칙) | DONE | esg-t1 BUG-P3-04 교훈 |

---

## Phase 2: AuditLog & Hash Chain (audit 모듈)

### AOP 인프라

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-2-01 | V5__audit_tables.sql — audit_logs, outbox_events | DONE | BIGSERIAL PK, Hash Chain 컬럼 |
| T-2-02 | `@Auditable` 어노테이션 정의 | DONE | shared/audit 패키지 @NamedInterface |
| T-2-03 | `test:` @Auditable 메서드 실행 → AuditLog 자동 기록 | DONE | |
| T-2-04 | `feat:` AuditAspect (Around advice) | DONE | Outbox 저장 경유 |
| T-2-05 | `feat:` DB Outbox Pattern — outbox_events 저장 | DONE | 트랜잭션 내 저장 |
| T-2-06 | `feat:` Outbox Poller — ApplicationEventPublisher | DONE | OutboxProcessingService(항상) + OutboxPollerScheduler(조건부) |
| T-2-07 | `test:` Outbox 처리 실패 → 재시도 후 완료 | DONE | AuditableIntegrationTest 4건 |

### Hash Chain

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-2-08 | `test:` Hash Chain 위변조 탐지 | DONE | HashChainCalculatorTest 7건 |
| T-2-09 | `feat:` HashChainCalculator 도메인 서비스 | DONE | SHA-256 |
| T-2-10 | `feat:` AuditLog 저장 — PESSIMISTIC_WRITE 락 + 해시 계산 | DONE | findFirstByTenantIdOrderByIdDesc @Lock |
| T-2-11 | `feat:` 무결성 검증 스케줄러 (매일 새벽 2시) | DONE | AuditIntegrityScheduler |
| T-2-12 | **[예방]** `feat:` 스케줄러 `zone = "Asia/Seoul"` + `@ConditionalOnProperty` 적용 | DONE | cron + zone + @ConditionalOnProperty |
| T-2-13 | **[예방]** `feat:` `canonicalPayload()` 단일 정적 메서드 (저장·검증 경로 동일) | DONE | HashChainCalculator.canonicalPayload() |
| T-2-14 | **[예방]** `test:` 해시 저장 경로와 검증 경로가 동일 직렬화 결과임을 단위 테스트로 검증 | DONE | |

---

## Phase 3: 배출계수 로더 & Scope 1/2 계산 엔진

### 배출계수 로더

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3-01 | V6__emission_factor_tables.sql | DONE | emission_factors (effective_from/to 포함) |
| T-3-02 | YAML 포맷 정의 — keei-2025.yaml 샘플 | DONE | 환경부 국가계수 4종 (KEEI SNAKE_CASE) |
| T-3-03 | YAML 포맷 정의 — defra-2025.yaml 샘플 | DONE | DEFRA 글로벌 계수 3종 |
| T-3-04 | `test:` 동일 파일 2회 로드 시 중복 없음 (멱등성) | DONE | EmissionFactorLoaderTest |
| T-3-05 | `test:` 값 수정 후 재로드 → 올바르게 업데이트 | DONE | |
| T-3-06 | `feat:` EmissionFactorLoader — item-level 멱등 upsert | DONE | SNAKE_CASE ObjectMapper 필수 |

### Scope 1/2 계산

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3-07 | V7__activity_emission_tables.sql | DONE | activity_data (+ V8 country_code), emission_records |
| T-3-08 | `test:` Scope 1 연료 연소 계산 (경유 1톤 → CO2e) | DONE | EmissionCalculatorTest |
| T-3-09 | `test:` Scope 2 location-based 전력 소비 계산 | DONE | EmissionCalculatorTest |
| T-3-10 | `test:` Scope 2 market-based (RE 인증서 적용) | DONE | EmissionCalculatorTest |
| T-3-11 | `test:` 배출계수 미존재 시 예외 처리 | DONE | EmissionCalculatorTest |
| T-3-12 | `feat:` EmissionCalculator 도메인 서비스 (순수 함수) | DONE | 음수 guard, scale=6 HALF_UP |
| T-3-13 | `feat:` EmissionFactorResolver (interface + DefaultImpl) | DONE | subCategory 포함 resolveAt |
| T-3-14 | `feat:` GWP 가중치 적용 (CO2e 환산) | DONE | GhgType.gwpAr6(), CO2E 기본 |
| T-3-15 | `feat:` POST /api/v1/ghg/entities/{id}/activity-data (@Auditable) | DONE | ActivityData.create() + Mapper |
| T-3-16 | `feat:` POST /api/v1/ghg/entities/{id}/calculations | DONE | /calculate→/calculations (REST 규칙) |
| T-3-17 | `feat:` GET /api/v1/ghg/entities/{id}/emission-records | DONE | |
| T-3-18 | **[예방]** `feat:` `EmissionFactorResolver.resolveAt(category, subCategory, countryCode, date)` | DONE | esg-t1 L-0-09 교훈 |
| T-3-19 | **[예방]** `test:` append-only 배출 기록 — 재산출 시 기존 기록 유지 확인 | DONE | GhgIntegrationTest |
| T-3-20 | **[예방]** `test:` `EmissionCalculator` BigDecimal scale=6 단위 테스트 | DONE | GhgIntegrationTest |

---

## Phase 3-B: 증빙 파일 관리 & 단위 변환 & ESG 지표 마스터

### Object Storage & 증빙 파일

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-01 | V9__evidence_tables.sql + V10__indicator_tables.sql | DONE | V9: evidence_files, activity_data_evidence / V10: esg_indicators, unit_conversions |
| T-3B-02 | `ObjectStorageGateway` 인터페이스 정의 | DONE | LocalStorageGateway 개발 구현체 |
| T-3B-03 | `test:` 파일 업로드 → SHA-256 검증 → 다운로드 테스트 | DONE | EvidenceFileServiceTest 4건 |
| T-3B-04 | `feat:` 파일 업로드 서비스 (DefaultEvidenceFileService) | DONE | DigestInputStream 단일 I/O |
| T-3B-05 | `feat:` 활동 데이터 ↔ 증빙 N:M 연결 | DONE | activity_data_evidence 테이블 (스키마) |
| T-3B-06 | `test:` SHA-256 불일치 파일 → 거부 테스트 | DONE | EvidenceFileServiceTest 포함 |

### 단위 변환

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-07 | `test:` GJ→kWh, TJ→GJ, Mcal→GJ 변환 정확도 | DONE | UnitConverterTest 7건 |
| T-3B-08 | `feat:` UnitConverter 도메인 서비스 | DONE | 12종 양방향 변환, scale=6 HALF_UP |
| T-3B-09 | `feat:` 활동 데이터 저장 시 원 단위 + 변환 단위 병행 저장 | DONE | ActivityData.create() 내 자동 변환 |

### ESG 지표 마스터 & Formula DSL

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-10 | S/G 기본 지표 초기 데이터 로드 | DONE | V11__indicator_seed.sql (8개 지표) |
| T-3B-11 | KSSB 2 프레임워크 매핑 YAML 로더 (멱등 upsert) | DONE | ON CONFLICT DO NOTHING (V11) |
| T-3B-12 | Scope 1/2 Formula YAML 초기 파일 작성 | DONE | EM-S1-FUEL, EM-S2-LB, EM-S2-MB |
| T-3B-13 | `test:` Formula test_cases 전체 통과 확인 | DONE | FormulaLoaderTest 7건 |
| T-3B-14 | `feat:` Formula DSL 로더 — test_cases 미통과 시 활성화 차단 | DONE | FormulaLoader + SimpleExpressionEvaluator |
| T-3B-15 | **[예방]** `test:` 경로 순회 공격 — `../../../etc/passwd` → 거부 | DONE | EvidenceFileServiceTest 포함 |
| T-3B-16 | **[예방]** `test:` 비허용 확장자(`.exe`) 업로드 → 거부 | DONE | EvidenceFileServiceTest 포함 |
| T-3B-17 | **[예방]** `feat:` `resolveContained(storageRoot, filename)` 경로 순회 방어 메서드 | DONE | LocalStorageGateway.resolveContained() |
| T-3B-18 | **[예방]** `test:` `DigestInputStream` 단일 I/O — 업로드 중 SHA-256 동시 계산 검증 | DONE | CountingDigestInputStream 패턴 |
| T-3B-19 | **[예방]** `test:` 활동 데이터 삭제 시도 → 물리 삭제 없이 비활성화 처리 확인 | DONE | ActivityDataRepository extends JpaRepository (status 필드 관리) |
| T-3B-20 | **[예방]** `test:` Formula DoS 한계값 초과 — depth 51 수식 → `FormulaValidationException` | DONE | FormulaConstants + FormulaLoaderTest |

### Phase 3 + 3-B 종합 재검토 수정 (2026-05-20)

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3R-01 | **[P0]** `activity_data` RLS 정책 추가 | DONE | `V12__activity_data_rls.sql` — ENABLE ROW LEVEL SECURITY + tenant_isolation |
| T-3R-02 | **[P0]** `evidence_files` RLS 정책 추가 | DONE | `V13__evidence_files_rls.sql` — ENABLE ROW LEVEL SECURITY + tenant_isolation |
| T-3R-03 | **[P0]** `ActivityDataResponse`, `EmissionRecordResponse`에서 `tenantId` 제거 | DONE | API 응답에 테넌트 식별자 노출 차단 |
| T-3R-04 | **[P1]** `EmissionFactorLoader.upsert()` — UPDATE → 비활성화+INSERT 방식으로 전환 | DONE | `deactivate(today-1)` + 새 레코드 INSERT(effective_from=today). `V14__fix_ef_unique_constraint.sql`로 UNIQUE 제약 교체 |
| T-3R-05 | **[P1]** `EmissionRecordRepository.findById()` 노출 | DONE | append-only Repository에 `Optional<EmissionRecordJpaEntity> findById(UUID id)` 추가 |
| T-3R-06 | **[P1]** `SimpleExpressionEvaluator` double 사용 목적 문서화 | DONE | 클래스 주석에 "test_cases 검증 전용 — 실제 배출량 계산은 BigDecimal" 명시 |
| T-3R-07 | **[P2]** `calculateEmissions()` 배출계수 캐싱 | DONE | `Map<String, EmissionFactor> factorCache` — 동일 카테고리 반복 DB 조회 방지 |
| T-3R-08 | **[P2]** `deriveScopeFromCategory()` SCOPE2_MB 구분 로직 수정 | DONE | `endsWith("_MB")` 선 체크 추가 |
| T-3R-09 | **[P3]** `ActivityDataJpaEntity` 상태 전이 메서드 추가 | DONE | `submit()`, `approve()`, `reject(reason)` — 직접 setStatus() 호출 방지 |

---

## Phase 4: 다법인 연결 집계 엔진

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-4-01 | V15__consolidated_emission_records.sql + V16__consolidated_rls.sql | DONE | P1: Append-only, RLS 적용 |
| T-4-02 | `test:` 3법인 Equity Method 연결 계산 정확도 | DONE | ConsolidationEngineTest |
| T-4-03 | `test:` 순환 지분 구조 탐지 → 예외 | DONE | ConsolidationEngineTest |
| T-4-04 | `test:` 이중 계상 제거 (A→B→C 체인) | DONE | ConsolidationEngineTest |
| T-4-05 | `test:` 지분율 수정 후 재계산 일관성 | DONE | ConsolidationEngineTest |
| T-4-06 | `feat:` ConsolidationEngine 도메인 서비스 | DONE | Equity / Operational Control, BigDecimal scale=6 |
| T-4-07 | `feat:` 이중 계상 제거 알고리즘 | DONE | effectiveOwnershipRatio 경로 곱 방식 |
| T-4-08 | `feat:` POST /api/v1/ghg/entities/{id}/consolidations | DONE | ConsolidationService + GhgController 엔드포인트 |
| T-4-09 | `feat:` GET /api/v1/ghg/entities/{id}/consolidations | DONE | 연결 집계 이력 조회 |
| T-4-10 | `test:` ConsolidationServiceIntegrationTest (통합 테스트 5건) | DONE | 105 tests passed |
| T-4-11 | Spring Modulith 경계: entity.api/ NamedInterface 선언 | DONE | ghg → entity::api 정상 접근, ModularityTest 통과 |

### Phase 4 재검토 수정 (2026-05-20)

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-4R-01 | **[P1]** `consolidateOperationalControl()` GHG Protocol 알고리즘 수정 | DONE | `hasDirectControlChain()` 도입 — 직접 지배 체인 각 링크 > 50% 판별. ConsolidationEngineTest +2건. 통합 테스트 합계 778.8로 수정 |
| T-4R-02 | **[P0]** `DefaultConsolidationService.consolidate()` rootEntityId 테넌트 소속 검증 | DONE | `EntityManagementService.findById(tenantId, entityId)` 추가 → 불일치 시 404 |
| T-4R-03 | **[P2]** `buildDirectEmissions()` N+1 쿼리 → IN 쿼리 일괄 조회 | DONE | `EmissionRecordRepository.findByTenantIdAndEntityIdInAndReportingYear()` 추가 |
| T-4R-04 | **[P2]** `findConsolidations()` N+1 쿼리 → `findByConsolidatedRecordIdIn()` + Java groupingBy | DONE | 집계 레코드 수만큼 발생하던 기여분 조회를 단일 IN 쿼리로 교체 |
| T-4R-05 | **[P2]** `persistContributions()` 개별 INSERT → `saveAll()` 배치 | DONE | `ConsolidatedEmissionContributionRepository.saveAll()` 추가 |
| T-4R-06 | **[P3]** `ConsolidationService` method 파라미터 String → ConsolidationMethod enum | DONE | 인터페이스·구현체·컨트롤러 일괄 교체. `parseMethod()` 제거 |
| T-4R-07 | **[P3]** `GlobalExceptionHandler` — MethodArgumentTypeMismatchException 핸들러 추가 | DONE | enum 변환 실패 시 500 → 400으로 수정 |
| T-4R-08 | **[P3]** `ConsolidationResponse`, `ConsolidationItemResponse` @Schema 전수 적용 | DONE | OpenAPI 자동 문서화 완성 |
| T-4R-09 | **[P3]** `GhgController` 연결 집계 엔드포인트 @Parameter 어노테이션 추가 | DONE | |
| T-4R-10 | **[P3]** `V17__add_contribution_constraints.sql` — ownership_ratio CHECK 제약 | DONE | `0 < ownership_ratio <= 1` |

---

## Phase 5: Scope 3 계산 엔진

### Cat.1 — 구매재화·서비스

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-01 | `test:` Cat.1 지출 기반 계산 정확도 | DONE | |
| T-5-02 | `feat:` Scope3Cat1Calculator | DONE | 지출 × 지출기반계수 |
| T-5-03 | `feat:` 데이터 품질 점수 자동 부여 | DONE | SPEND_BASED/AVERAGE_DATA/SUPPLIER_SPECIFIC |

### Cat.2 — 자본재

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-04 | `test:` Cat.2 자본재 취득액 → CO2e | DONE | |
| T-5-05 | `feat:` Scope3Cat2Calculator | DONE | |

### Cat.11 — 판매제품 사용

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-06 | `test:` Cat.11 다년도 배출 현재연도 귀속 | DONE | |
| T-5-07 | `feat:` Scope3Cat11Calculator | DONE | 판매량 × 계수 × 사용기간 |

### Scope 3 Coverage Report

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-08 | V18__scope3_tables.sql | DONE | scope3_coverage_reports + scope3_category CHECK 1~16 |
| T-5-09 | `test:` 95% 임계값 판단 정확도 | DONE | |
| T-5-10 | `feat:` Scope3CoverageCalculator | DONE | |
| T-5-11 | `feat:` Scope3Service + GhgController 5개 엔드포인트 | DONE | POST cat1/2/11/coverage-report, GET coverage-report |
| T-5-12 | `feat:` Category 16 DB 스키마 준비 (계산 미구현) | DONE | scope3_category CHECK 1~16 |

---

## Phase 6: 데이터 수집 파이프라인

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-6-01 | `test:` CSV 100행 업로드 멱등성 | DONE | 재업로드 시 100건 SKIPPED 검증 |
| T-6-02 | `feat:` CSV 파싱 + 유효성 검사 + item-level 중복 방지 | DONE | CsvActivityDataParser + ActivityDataRowImporter |
| T-6-03 | `feat:` 오류 행 리포트 API 응답 | DONE | CsvUploadResponse.nonSuccessRows |
| T-6-04 | `feat:` Webhook 수신 엔드포인트 (POST /api/v1/intake/tenants/{id}/webhook) | DONE | HMAC-SHA256 IntakeController |
| T-6-05 | `test:` Webhook 시그니처 검증 실패 → 401 | DONE | |
| T-6-06 | `feat:` 데이터 정규화 파이프라인 (ERP → ActivityData) | DONE | WebhookActivityDataItem → CsvRow → ActivityData |
| T-6-07 | `feat:` SUPPLIER 계정 초대 + 이메일 발송 | TODO | Phase 6B-supply |
| T-6-08 | `feat:` POST /api/v1/supplier/activity-data (자사 데이터만) | TODO | Phase 6B-supply |
| T-6-09 | `test:` 공급업체 → 타사 데이터 접근 시도 → 403 | TODO | Phase 6B-supply |
| T-6-10 | `feat:` 공급업체 제출 → ESG_MANAGER 승인 워크플로우 | TODO | Phase 6B-supply |
| T-6-11 | `feat:` 미제출 법인 자동 리마인더 스케줄러 | TODO | Phase 6B-supply |
| T-6-12 | **[예방]** `test:` CSV 중간 행 오류 시 이전 행 보존 확인 (`REQUIRES_NEW` 트랜잭션) | DONE | |
| T-6-13 | **[예방]** `test:` 중복 항목 재업로드 → WARN 로그 + 계속 처리 (ERROR 없음) | DONE | |
| T-6-14 | **[예방]** `feat:` CSV 업로드 행별 독립 `@Transactional(REQUIRES_NEW)` 적용 | DONE | ActivityDataRowImporter |
| T-6-15 | **[예방]** `test:` `@Async` 메서드에서 `@Transactional` 없음 확인 (별도 빈 분리) | DONE | ClassPathScanningCandidateComponentProvider 사용 |

### Phase 6A 재검토 수정 (2026-05-20)

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-6R-01 | **[P1]** `test:` CSV 음수 quantity → ERROR 반환 + 존재하지 않는 entityId → 예외 | DONE | IntakeIntegrationTest +2건 |
| T-6R-02 | **[P1]** `fix:` `CsvActivityDataParser.parse()` 오류 → 500 반환 수정 | DONE | `DefaultIntakeService`에서 `IllegalArgumentException` → `EsgException(CSV_PARSE_FAILED)` 래핑. 400 반환 |
| T-6R-03 | **[P1]** `fix:` Webhook `TenantContextInterceptor` RLS 갭 수정 | DONE | WEBHOOK_TENANT_PATTERN fallback 추가 — JWT 없는 경로에서도 `app.current_tenant_id` 설정 |
| T-6R-04 | **[P1]** `fix:` CSV/Webhook `entityId` 테넌트 소속 검증 추가 | DONE | `EntityManagementService.findById(tenantId, entityId)` 적용 (Phase 4 ConsolidationService 패턴 동일) |
| T-6R-05 | **[P2]** `fix:` `CsvActivityDataParser` — Spring `Resource` → `InputStream` 시그니처 변경 | DONE | `domain/` 패키지 순수 Java 원칙 회복 |
| T-6R-06 | **[P3]** `fix:` `ActivityDataRowImporter` — 음수 quantity 검증 추가 | DONE | `quantity.signum() <= 0` → ERROR 반환 |
| T-6R-07 | **[P3]** `docs:` `IntakeController.receiveWebhook()` `@PreAuthorize` 면제 사유 주석 | DONE | HMAC 인증 대체 명시 |

---

## Phase 6-B: 정정·재공시 워크플로우 & Formula DSL 배포

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-6B-01 | `test:` 활동 데이터 정정 → 새 버전 INSERT, 원본 불변 확인 | TODO | |
| T-6B-02 | `test:` 정정 사유 코드 누락 → 예외 | TODO | |
| T-6B-03 | `feat:` POST /api/v1/ghg/activity-data/{id}/correct @Auditable | TODO | |
| T-6B-04 | `feat:` 정정 이벤트 → 배출량 재산출 자동 트리거 | TODO | |
| T-6B-05 | `feat:` GET /api/v1/ghg/activity-data/{id}/versions | TODO | 버전 이력 |
| T-6B-06 | `feat:` 정정 전·후 수치 비교 API (/diff) | TODO | |
| T-6B-07 | `test:` Formula YAML test_cases 실패 → 활성화 차단 | TODO | |
| T-6B-08 | `feat:` Formula 버전 관리 (활성/비활성) | TODO | |
| T-6B-09 | `feat:` 산식 변경 → 영향받는 배출량 목록 조회 API | TODO | |

---

## Phase 7: 공시 보고서 생성 (rpt 모듈)

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-7-01 | V12__report_tables.sql | TODO | disclosure_reports |
| T-7-02 | `test:` Scope 1·2·3 합산 수치 정확도 | TODO | |
| T-7-03 | `test:` YoY 비교 (전년 데이터 없을 경우 N/A) | TODO | |
| T-7-04 | `test:` DRAFT 보고서 → 스냅샷 생성 불가 | TODO | |
| T-7-05 | `feat:` ReportBuilder 도메인 서비스 | TODO | |
| T-7-06 | `feat:` KSSB 2 지표·목표 섹션 자동 생성 | TODO | |
| T-7-07 | `feat:` YoY 비교 자동 계산 | TODO | |
| T-7-08 | `feat:` 보고서 승인 워크플로우 (DRAFT → SUBMITTED → APPROVED) | TODO | @Auditable |
| T-7-09 | `feat:` PDF 렌더링 (POST /api/v1/reports/{id}/pdf) | TODO | Apache PDFBox |
| T-7-10 | `feat:` iXBRL XBRL taxonomy 매핑 데이터 모델 설계 | TODO | 렌더링은 M+1 |
| T-7-11 | **[예방]** `feat:` ApprovalEntity — `approve()`, `reject(reason)`, `escalate()` 메서드만 노출 | TODO | esg-t1 교훈: setStatus() 직접 호출 금지 |
| T-7-12 | **[예방]** `test:` `reject(reason)` — reason 공백 → `EsgException(REJECTION_REASON_REQUIRED)` | TODO | |
| T-7-13 | **[예방]** `test:` `AuditLogRepository`에서 `delete` 메서드 호출 컴파일 오류 확인 (`Repository<T,ID>` 마커) | TODO | esg-t1 BUG-P3-07 교훈 |

---

## Phase 8: 외부 검증 워크스페이스 (vw 모듈)

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-8-01 | V13__verification_tables.sql | TODO | verification_snapshots, verification_comments |
| T-8-02 | PostgreSQL 트리거: 스냅샷 UPDATE/DELETE 차단 | TODO | |
| T-8-03 | `test:` 스냅샷 생성 후 원본 수정 → 스냅샷 불변 확인 | TODO | |
| T-8-04 | `test:` VERIFIER → 지정 스냅샷 외 접근 → 403 | TODO | |
| T-8-05 | `test:` 미승인 보고서 → 스냅샷 생성 시도 → 예외 | TODO | |
| T-8-06 | `feat:` VerificationSnapshot 도메인 (SHA-256 해시) | TODO | |
| T-8-07 | `feat:` 스냅샷 생성 API (POST /api/v1/vw/snapshots) | TODO | APPROVED 보고서만 |
| T-8-08 | `feat:` VERIFIER RLS 정책 (지정 snapshot_id만) | TODO | |
| T-8-09 | `feat:` 코멘트 CRUD (POST /api/v1/vw/snapshots/{id}/comments) | TODO | @Auditable |
| T-8-10 | `feat:` 검증 완료 서명 (POST /api/v1/vw/snapshots/{id}/sign) | TODO | @Auditable |

---

## Phase 9: 프론트엔드 — 기반 & 데이터 입력 UI

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-9-01 | App Router 기반 레이아웃 (사이드바 네비게이션) | TODO | |
| T-9-02 | 로그인 / 토큰 갱신 / 로그아웃 플로우 | TODO | |
| T-9-03 | 법인 관리 UI — 트리 시각화 | TODO | |
| T-9-04 | 법인 관리 UI — 지분율 설정 | TODO | |
| T-9-05 | 활동 데이터 입력 폼 (Scope 카테고리 선택) | TODO | Zod 유효성 |
| T-9-06 | CSV 업로드 UI — 컬럼 매핑 | TODO | |
| T-9-07 | CSV 업로드 UI — 오류 행 표시 | TODO | |
| T-9-08 | 데이터 승인 워크플로우 UI | TODO | |
| T-9-09 | RBAC 기반 메뉴 분기 (역할별 가시성) | TODO | |

---

## Phase 10: 프론트엔드 — GHG 대시보드 & 보고서 UI

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-10-01 | GHG 대시보드 — 연도별·법인별·Scope별 차트 | TODO | Recharts |
| T-10-02 | Scope 3 카테고리별 분류 차트 | TODO | |
| T-10-03 | Scope 3 95% 커버리지 보고서 UI | TODO | |
| T-10-04 | 연결 vs 법인별 뷰 전환 토글 | TODO | |
| T-10-05 | 보고서 생성 위저드 UI | TODO | |
| T-10-06 | PDF 다운로드 버튼 | TODO | |
| T-10-07 | 보고서 승인 워크플로우 UI | TODO | |

---

## Phase 11: 프론트엔드 — 검증 워크스페이스 & 공급업체 포털

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-11-01 | 검증 워크스페이스 — 스냅샷 목록 조회 (VERIFIER 전용) | TODO | |
| T-11-02 | 검증 워크스페이스 — 스냅샷 상세 (배출량·증거·코멘트) | TODO | |
| T-11-03 | 검증 워크스페이스 — 코멘트 작성 | TODO | |
| T-11-04 | 검증 워크스페이스 — 검증 완료 서명 UI | TODO | |
| T-11-05 | 공급업체 포털 — 초대 → 계정 설정 온보딩 | TODO | |
| T-11-06 | 공급업체 포털 — 활동 데이터 입력 | TODO | |
| T-11-07 | 공급업체 포털 — 제출 현황 조회 | TODO | |
| T-11-08 | AuditLog UI — 날짜·엔티티 필터 조회 (TENANT_ADMIN) | TODO | |
| T-11-09 | AuditLog UI — Hash Chain 무결성 보고서 | TODO | |

---

## Phase 12: 통합 검증 & 최적화 & 보안 감사

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-12-01 | Virtual Threads 활성화 + Scope 3 배치 처리 벤치마크 | TODO | 100개 공급업체 ≤ 30초 |
| T-12-02 | k6 부하 테스트 (P95 500ms, 동시 100명) | TODO | |
| T-12-03 | OWASP ZAP 자동 스캔 | TODO | High/Critical 0건 목표 |
| T-12-04 | SQL Injection 방어 검증 | TODO | PreparedStatement 전수 확인 |
| T-12-05 | IDOR 취약점 테스트 (크로스 테넌트) | TODO | |
| T-12-06 | JWT 탈취 시나리오 테스트 (블랙리스트 로그아웃) | TODO | |
| T-12-07 | RLS 우회 시도 테스트 | TODO | |
| T-12-08 | KSSB 2 필수 공시 항목 체크리스트 전수 확인 | TODO | |
| T-12-09 | Scope 3 95% 임계값 보고서 정확도 재검증 | TODO | |
| T-12-10 | Hash Chain 24시간 운영 후 무결성 확인 | TODO | |
| T-12-11 | OpenAPI 3.1 문서 최종 검토 | TODO | |
| T-12-12 | docs/insight.md 주요 교훈 정리 | TODO | |
| T-12-13 | docs/adr/ ADR 최종 검토 및 보완 | TODO | |
| T-12-14 | **[예방]** `EmissionCalculator` 코드 전체 `float`/`double` grep — 0건 확인 | TODO | BigDecimal 전수 감사 |
| T-12-15 | **[예방]** N+1 쿼리 검증 — Hibernate statistics로 주요 API 3개 쿼리 수 측정 | TODO | |
| T-12-16 | **[예방]** 전체 컨트롤러 `@PreAuthorize` 누락 엔드포인트 grep 검증 — 0건 확인 | TODO | |
| T-12-17 | Legacy Data Migration 배치 스펙 정의 | TODO | 엑셀·타 시스템 과거 데이터 이관 배치 Job 설계 (ADR-009 작성 포함) |
| T-12-18 | Legacy Data Migration 배치 구현 및 검증 | TODO | CSV/Excel 업로드 → staging → 검증 → audit_logs 생성 순 처리 |

---

## 추적 메트릭

| 지표 | 목표 | 측정 시점 |
|---|---|---|
| 전체 태스크 완료율 | 100% (Phase 12 종료 시) | 각 Phase 종료 |
| 단위 테스트 커버리지 | ≥ 80% (도메인 레이어) | CI 자동 측정 |
| 통합 테스트 통과율 | 100% | CI 자동 |
| ModularityTest 통과율 | 100% | CI 자동 |
| AuditLog 누락 건수 | 0건 | Phase 12 검증 |
| OWASP ZAP High/Critical | 0건 | Phase 12 |

---

## 검토 이력

| 날짜 | 검토자 | 버전 | 주요 변경 |
|---|---|---|---|
| 2026-05-18 | Claude Code (esg-t2 기획) | 1.0 | 초안 작성 |
| 2026-05-19 | Claude Code (Phase 0 완료) | 1.1 | Phase 0 전체 DONE, T-0-10 SKIP, T-0-12 버전 수정 |
| 2026-05-19 | Claude Code (Gemini 리뷰 반영) | 1.2 | T-12-17~18 Legacy Migration 태스크 추가 |
