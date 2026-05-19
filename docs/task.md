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
| T-1-03 | `test:` LegalEntity 생성 실패 케이스 (필수 필드 누락, 국가코드 유효성) | TODO | |
| T-1-04 | `feat:` LegalEntity 도메인 + 팩토리 메서드 | TODO | `LegalEntity.create(cmd)` |
| T-1-05 | `test:` EntityRelationship 지분율 범위 유효성 (0~1) | TODO | |
| T-1-06 | `feat:` EntityRelationship 도메인 + 순환 참조 방지 | TODO | DAG 사이클 탐지 |
| T-1-07 | `test:` EntityRelationshipGraph 트리 탐색 | TODO | 부모→자식 경로 순회 |
| T-1-08 | `feat:` EntityRelationshipGraph 구현 | TODO | |

### 보안·API

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-1-09 | `test:` TENANT_ADMIN만 법인 생성 가능 (보안 테스트) | TODO | |
| T-1-10 | `feat:` Spring Security RBAC 설정 + JwtAuthenticationFilter | TODO | 6개 역할, HMAC-SHA256 자체 JWT |
| T-1-11 | **[필수]** `feat:` TenantContextInterceptor — 요청마다 `SET LOCAL app.current_tenant_id` | TODO | RLS 전제 조건 — SecurityFilter 이후, API 이전에 위치 |
| T-1-12 | `feat:` JWT 발급 (POST /api/v1/auth/login) | TODO | Access 15분, Refresh 7일 |
| T-1-13 | `feat:` JWT 갱신 (POST /api/v1/auth/refresh) | TODO | |
| T-1-14 | `feat:` 로그아웃 — Redis 블랙리스트 (POST /api/v1/auth/logout) | TODO | |
| T-1-15 | `feat:` 법인 등록 API (POST /api/v1/entities) | TODO | @Auditable 어노테이션 부착 (AOP는 Phase 2) |
| T-1-16 | `feat:` 법인 목록 API (GET /api/v1/entities) | TODO | 트리 구조 응답 |
| T-1-17 | `feat:` 관계 설정 API (PUT /api/v1/entities/{id}/relationships) | TODO | |
| T-1-18 | **[예방]** `test:` `AccessDeniedException` → 403 응답 확인 (미처리 시 500) | TODO | esg-t1 L-0-10 교훈 |
| T-1-19 | **[예방]** `test:` `ObjectOptimisticLockingFailureException` → 409 응답 확인 | TODO | |
| T-1-20 | **[예방]** `test:` ERROR severity 검증 실패 → `create()` 차단 확인 (검증 우선 원칙) | TODO | esg-t1 BUG-P3-04 교훈 |

---

## Phase 2: AuditLog & Hash Chain (audit 모듈)

### AOP 인프라

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-2-01 | V5__audit_tables.sql — audit_logs, outbox_events | TODO | BIGSERIAL PK, Hash Chain 컬럼 |
| T-2-02 | `@Auditable` 어노테이션 정의 | TODO | shared 패키지 |
| T-2-03 | `test:` @Auditable 메서드 실행 → AuditLog 자동 기록 | TODO | |
| T-2-04 | `feat:` AuditAspect (Around advice) | TODO | Before/After 상태 캡처 |
| T-2-05 | `feat:` DB Outbox Pattern — outbox_events 저장 | TODO | 트랜잭션 내 저장 |
| T-2-06 | `feat:` Outbox Poller — ApplicationEventPublisher | TODO | 1초 간격 폴링 |
| T-2-07 | `test:` Outbox 처리 실패 → 재시도 후 완료 | TODO | |

### Hash Chain

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-2-08 | `test:` Hash Chain 위변조 탐지 | TODO | 중간 항목 수정 시 이후 무효화 |
| T-2-09 | `feat:` HashChainCalculator 도메인 서비스 | TODO | SHA-256 |
| T-2-10 | `feat:` AuditLog 저장 — PESSIMISTIC_WRITE 락 + 해시 계산 | TODO | esg-t1 synchronized 버그 교훈 |
| T-2-11 | `feat:` 무결성 검증 스케줄러 (매일 새벽 2시) | TODO | 불일치 시 알림 이벤트 |
| T-2-12 | **[예방]** `feat:` 스케줄러 `zone = "Asia/Seoul"` + `@ConditionalOnProperty` 적용 | TODO | esg-t1 BUG-P4-12 교훈 |
| T-2-13 | **[예방]** `feat:` `canonicalPayload()` 단일 정적 메서드 (저장·검증 경로 동일) | TODO | esg-t1 L-0-08 교훈 |
| T-2-14 | **[예방]** `test:` 해시 저장 경로와 검증 경로가 동일 직렬화 결과임을 단위 테스트로 검증 | TODO | |

---

## Phase 3: 배출계수 로더 & Scope 1/2 계산 엔진

### 배출계수 로더

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3-01 | V6__emission_factor_tables.sql | TODO | emission_factors, factor_versions |
| T-3-02 | YAML 포맷 정의 — keei-2025.yaml 샘플 | TODO | 환경부 국가계수 |
| T-3-03 | YAML 포맷 정의 — defra-2025.yaml 샘플 | TODO | DEFRA 글로벌 계수 |
| T-3-04 | `test:` 동일 파일 2회 로드 시 중복 없음 (멱등성) | TODO | item-level upsert |
| T-3-05 | `test:` 값 수정 후 재로드 → 올바르게 업데이트 | TODO | |
| T-3-06 | `feat:` EmissionFactorLoader — item-level 멱등 upsert | TODO | ON CONFLICT DO UPDATE |

### Scope 1/2 계산

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3-07 | V7__activity_emission_tables.sql | TODO | activity_data, emission_records |
| T-3-08 | `test:` Scope 1 연료 연소 계산 (경유 1톤 → CO2e) | TODO | |
| T-3-09 | `test:` Scope 2 location-based 전력 소비 계산 | TODO | |
| T-3-10 | `test:` Scope 2 market-based (RE 인증서 적용) | TODO | |
| T-3-11 | `test:` 배출계수 미존재 시 예외 처리 | TODO | |
| T-3-12 | `feat:` EmissionCalculator 도메인 서비스 (순수 함수) | TODO | |
| T-3-13 | `feat:` EmissionFactorResolver (interface + DefaultImpl) | TODO | country·category·year 매칭 |
| T-3-14 | `feat:` GWP 가중치 적용 (CO2e 환산) | TODO | IPCC AR6 기준 |
| T-3-15 | `feat:` POST /api/v1/ghg/activity-data (@Auditable) | TODO | |
| T-3-16 | `feat:` POST /api/v1/ghg/calculate?scope=SCOPE1,SCOPE2 | TODO | |
| T-3-17 | `feat:` GET /api/v1/ghg/emission-records | TODO | 필터링 지원 |
| T-3-18 | **[예방]** `feat:` `EmissionFactorResolver.resolveAt(category, date)` — 과거 산출 시점 계수 조회 | TODO | esg-t1 L-0-09 교훈 |
| T-3-19 | **[예방]** `test:` 배출계수 갱신 후 과거 산출 수치 동일성 확인 (재현성 테스트) | TODO | |
| T-3-20 | **[예방]** `test:` `EmissionCalculator` BigDecimal 사용 단위 테스트 (float/double 없음 확인) | TODO | |

---

## Phase 3-B: 증빙 파일 관리 & 단위 변환 & ESG 지표 마스터

### Object Storage & 증빙 파일

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-01 | V8__evidence_tables.sql + V9__indicator_tables.sql | TODO | V8: evidence_files, activity_data_evidence / V9: esg_indicators, unit_conversions |
| T-3B-02 | `ObjectStorageGateway` 인터페이스 정의 | TODO | MinIO 개발 구현체 |
| T-3B-03 | `test:` 파일 업로드 → SHA-256 검증 → 다운로드 테스트 | TODO | |
| T-3B-04 | `feat:` 파일 업로드 API (POST /api/v1/evidence) | TODO | DigestInputStream 단일 I/O |
| T-3B-05 | `feat:` 활동 데이터 ↔ 증빙 N:M 연결 | TODO | @Auditable |
| T-3B-06 | `test:` SHA-256 불일치 파일 → 거부 테스트 | TODO | |

### 단위 변환

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-07 | `test:` GJ→kWh, TJ→GJ, Mcal→GJ 변환 정확도 | TODO | |
| T-3B-08 | `feat:` UnitConverter 도메인 서비스 | TODO | 기준 단위 양방향 변환 |
| T-3B-09 | `feat:` 활동 데이터 저장 시 원 단위 + 변환 단위 병행 저장 | TODO | |

### ESG 지표 마스터 & Formula DSL

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-3B-10 | S/G 기본 지표 초기 데이터 로드 | TODO | 여성관리자비율, 산재율, 이직률, 이사회출석률 |
| T-3B-11 | KSSB 2 프레임워크 매핑 YAML 로더 (멱등 upsert) | TODO | |
| T-3B-12 | Scope 1/2 Formula YAML 초기 파일 작성 | TODO | EM-S1-FUEL, EM-S2-LB, EM-S2-MB |
| T-3B-13 | `test:` Formula test_cases 전체 통과 확인 | TODO | |
| T-3B-14 | `feat:` Formula DSL 로더 — test_cases 미통과 시 활성화 차단 | TODO | |
| T-3B-15 | **[예방]** `test:` 경로 순회 공격 — `../../../etc/passwd` → `INVALID_FILE_PATH` | TODO | esg-t1 BUG-P3-09 교훈 |
| T-3B-16 | **[예방]** `test:` 비허용 확장자(`.exe`) 업로드 → 거부 | TODO | |
| T-3B-17 | **[예방]** `feat:` `resolveContained(storageRoot, filename)` 경로 순회 방어 메서드 | TODO | |
| T-3B-18 | **[예방]** `test:` `DigestInputStream` 단일 I/O — 업로드 중 SHA-256 동시 계산 검증 | TODO | esg-t1 L-0-06 교훈 |
| T-3B-19 | **[예방]** `test:` 활동 데이터 삭제 시도 → 물리 삭제 없이 비활성화 처리 확인 | TODO | |
| T-3B-20 | **[예방]** `test:` Formula DoS 한계값 초과 — depth 51 수식 → `FormulaValidationException` | TODO | esg-t1 BUG-P5-03 교훈 |

---

## Phase 4: 다법인 연결 집계 엔진

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-4-01 | V10__consolidated_tables.sql | TODO | |
| T-4-02 | `test:` 3법인 Equity Method 연결 계산 정확도 | TODO | |
| T-4-03 | `test:` 순환 지분 구조 탐지 → 예외 | TODO | |
| T-4-04 | `test:` 이중 계상 제거 (A→B→C 체인) | TODO | |
| T-4-05 | `test:` 지분율 수정 후 재계산 일관성 | TODO | |
| T-4-06 | `feat:` ConsolidationEngine 도메인 서비스 | TODO | Equity / Operational Control |
| T-4-07 | `feat:` 이중 계상 제거 알고리즘 | TODO | |
| T-4-08 | `feat:` GET /api/v1/entities/{id}/consolidated | TODO | |
| T-4-09 | `feat:` GET /api/v1/entities/{id}/consolidated?view=individual | TODO | |

---

## Phase 5: Scope 3 계산 엔진

### Cat.1 — 구매재화·서비스

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-01 | `test:` Cat.1 지출 기반 계산 정확도 | TODO | |
| T-5-02 | `feat:` Scope3Cat1Calculator | TODO | 지출 × 지출기반계수 |
| T-5-03 | `feat:` 데이터 품질 점수 자동 부여 | TODO | SPEND_BASED/AVERAGE_DATA/SUPPLIER_SPECIFIC |

### Cat.2 — 자본재

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-04 | `test:` Cat.2 자본재 취득액 → CO2e | TODO | |
| T-5-05 | `feat:` Scope3Cat2Calculator | TODO | |

### Cat.11 — 판매제품 사용

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-06 | `test:` Cat.11 다년도 배출 현재연도 귀속 | TODO | |
| T-5-07 | `feat:` Scope3Cat11Calculator | TODO | 판매량 × 계수 × 사용기간 |

### Scope 3 Coverage Report

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-5-08 | V11__scope3_tables.sql | TODO | scope3_coverage_reports |
| T-5-09 | `test:` 95% 임계값 판단 정확도 | TODO | |
| T-5-10 | `feat:` Scope3CoverageCalculator | TODO | |
| T-5-11 | `feat:` GET /api/v1/ghg/scope3-coverage-report | TODO | |
| T-5-12 | `feat:` Category 16 DB 스키마 준비 (계산 미구현) | TODO | scope3_category CHECK 1~16 |

---

## Phase 6: 데이터 수집 파이프라인

| ID | 태스크 | 상태 | 비고 |
|---|---|---|---|
| T-6-01 | `test:` CSV 100행 업로드 멱등성 | TODO | |
| T-6-02 | `feat:` CSV 파싱 + 유효성 검사 + item-level 중복 방지 | TODO | |
| T-6-03 | `feat:` 오류 행 리포트 API 응답 | TODO | |
| T-6-04 | `feat:` Webhook 수신 엔드포인트 (POST /api/v1/intake/webhook) | TODO | HMAC-SHA256 시그니처 검증 |
| T-6-05 | `test:` Webhook 시그니처 검증 실패 → 401 | TODO | |
| T-6-06 | `feat:` 데이터 정규화 파이프라인 (ERP → ActivityData) | TODO | |
| T-6-07 | `feat:` SUPPLIER 계정 초대 + 이메일 발송 | TODO | |
| T-6-08 | `feat:` POST /api/v1/supplier/activity-data (자사 데이터만) | TODO | RLS 강제 |
| T-6-09 | `test:` 공급업체 → 타사 데이터 접근 시도 → 403 | TODO | |
| T-6-10 | `feat:` 공급업체 제출 → ESG_MANAGER 승인 워크플로우 | TODO | |
| T-6-11 | `feat:` 미제출 법인 자동 리마인더 스케줄러 | TODO | |
| T-6-12 | **[예방]** `test:` CSV 중간 행 오류 시 이전 행 보존 확인 (`REQUIRES_NEW` 트랜잭션) | TODO | esg-t1 Phase 3 교훈 |
| T-6-13 | **[예방]** `test:` 중복 항목 재업로드 → WARN 로그 + 계속 처리 (ERROR 없음) | TODO | |
| T-6-14 | **[예방]** `feat:` CSV 업로드 행별 독립 `@Transactional(REQUIRES_NEW)` 적용 | TODO | |
| T-6-15 | **[예방]** `test:` `@Async` 메서드에서 `@Transactional` 없음 확인 (별도 빈 분리) | TODO | esg-t1 Phase 8 교훈 |

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
