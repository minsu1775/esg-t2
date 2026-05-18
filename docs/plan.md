# ESG 공시지원 시스템 esg-t2 — 구현 계획서 (Plan)

> **버전**: 1.0  
> **작성일**: 2026-05-18  
> **레퍼런스**: prd.md v1.0, spec.md v1.0, esg-t1/docs/plan.md  
> **전략**: esg-t1 Phase 0~12 교훈을 반영한 견고한 TDD 기반 점진적 구축

---

## 개요

### 개발 원칙

1. **TDD 강제**: Red(test:) → Green(feat:) → Refactor(refactor:) 순서 준수
2. **esg-t1 버그 선제 차단**: 각 Phase의 DoD에 이전 프로젝트 교훈 항목 포함
3. **Spring Modulith 경계 검증**: `ModularityTest`를 Phase 1부터 유지
4. **@Auditable AOP 우선 구축**: Phase 2에서 완성, 이후 모든 데이터 변경에 적용
5. **규제 일정 하드코딩 금지**: `disclosure_schedule` 테이블 우선 구축

### Phase 구조

```
Phase 0: 프로젝트 셋업 & 인프라 기반
Phase 1: 법인·테넌트 관리 (entity 모듈)
Phase 2: AuditLog & Hash Chain (audit 모듈)
Phase 3: GHG 배출계수 로더 & Scope 1/2 계산 엔진
Phase 3-B: 증빙 파일 관리 & 단위 변환 & ESG 지표 마스터
Phase 4: 다법인 연결 집계 엔진
Phase 5: Scope 3 계산 엔진 (Category 1·2·11)
Phase 6: 데이터 수집 파이프라인 (CSV·API·공급업체 포털)
Phase 6-B: 정정·재공시 워크플로우 & Formula DSL 배포
Phase 7: 공시 보고서 생성 (rpt 모듈)
Phase 8: 외부 검증 워크스페이스 (vw 모듈)
Phase 9: 프론트엔드 — 기반 & 데이터 입력 UI
Phase 10: 프론트엔드 — GHG 대시보드 & 보고서 UI
Phase 11: 프론트엔드 — 검증 워크스페이스 UI & 공급업체 포털
Phase 12: 통합 검증 & 성능 최적화 & 보안 감사
```

---

## Phase 0: 프로젝트 셋업 & 인프라 기반

**목표**: 코드 한 줄 없이도 CI/CD, DB, 모듈 뼈대가 동작하는 상태

### 작업 목록

- [ ] Spring Boot 4 + Java 25 Gradle 프로젝트 생성 (루트 + `frontend/` 서브 프로젝트)
- [ ] Spring Boot 4.0.x + Java 25 기본 설정
- [ ] Spring Modulith 의존성 추가 + 모듈 패키지 생성 (ghg, entity, vw, rpt, supply, audit, shared)
- [ ] PostgreSQL 18 Docker Compose 설정
- [ ] Redis Docker Compose 설정
- [ ] Flyway 설정 + V1__initial_schema.sql (tenants + disclosure_schedules), V2__disclosure_schedule_seed.sql (초기 일정 데이터)
- [ ] Testcontainers 기반 통합 테스트 설정 (`AbstractIntegrationTest`)
- [ ] `ModularityTest` 초기 설정 (모듈 경계 검증 자동화)
- [ ] GitHub Actions CI 파이프라인 (test → modularity-check → build)
- [ ] OpenTelemetry 설정 (Spring Boot 4 내장 활성화)
- [ ] Prometheus 메트릭 endpoint 노출
- [ ] Next.js 15 프로젝트 초기화 (TypeScript strict, Tailwind 4.x)

### 완료 기준 (DoD)

- [ ] `./gradlew test` 통과 (테스트 없어도 빌드 성공)
- [ ] `./gradlew test --tests "*ModularityTest"` 통과
- [ ] Docker Compose up 후 `/actuator/health` 200 응답
- [ ] `/actuator/prometheus` 메트릭 노출 확인
- [ ] `disclosure_schedule` 초기 데이터 Flyway 마이그레이션 성공
- [ ] **[예방]** Flyway locations: `db/migration` (공통) + `db/migration-pg` (PG 전용) 분리 확인
- [ ] **[예방]** `application-test.yml`에 `scheduler.enabled: false` 설정 확인 (스케줄러 테스트 격리)
- [ ] **[예방]** `AbstractIntegrationTest`에 `static { POSTGRES.start(); }` 패턴 확인 (컨테이너 초기화 순서)

---

## Phase 1: 법인·테넌트 관리 (entity 모듈)

**목표**: 다법인 계층 구조를 생성·조회·수정할 수 있는 API와 도메인

### 작업 목록

#### TDD 사이클

- [ ] `test:` LegalEntity 생성 실패 케이스 (필수 필드 누락)
- [ ] `feat:` LegalEntity 도메인 객체 + 팩토리 메서드
- [ ] `test:` EntityRelationship 지분율 유효성 검사 (0~1 범위)
- [ ] `feat:` EntityRelationship 도메인 + 순환 참조 방지 로직
- [ ] `test:` 법인 계층 DAG 순회 (부모→자식 트리 탐색)
- [ ] `feat:` EntityRelationshipGraph 구현
- [ ] `test:` TENANT_ADMIN 역할만 법인 생성 가능 (보안 테스트)
- [ ] `feat:` Spring Security RBAC 설정

#### DB

- [ ] V3__entity_tables.sql (legal_entities, entity_relationships)
- [ ] V4__auth_tables.sql (users, user_roles)
- [ ] `feat:` TenantContextInterceptor — 요청마다 `SET LOCAL app.current_tenant_id = '...'` 실행 (RLS 동작 전제 조건)
- [ ] RLS 정책 적용 (tenant_id 기반)

#### API

- [ ] `POST /api/v1/entities` 법인 등록
- [ ] `GET /api/v1/entities` 법인 목록 (트리 구조)
- [ ] `PUT /api/v1/entities/{id}/relationships` 관계 설정

#### JWT 인증

- [ ] `POST /api/v1/auth/login`
- [ ] `POST /api/v1/auth/refresh`
- [ ] Redis 블랙리스트 기반 로그아웃

### 완료 기준 (DoD)

- [ ] 단위 테스트: 도메인 팩토리 케이스 ≥ 5개 (경계값 포함)
- [ ] 통합 테스트: Testcontainers PostgreSQL — CRUD + RLS 격리 확인
- [ ] ModularityTest 통과 (entity 모듈 경계 검증)
- [ ] 3개 법인 계층 생성 → 트리 조회 API 검증
- [ ] **[예방]** `GlobalExceptionHandler`에 `AccessDeniedException` → 403 핸들러 등록 확인 (미등록 시 500 반환)
- [ ] **[예방]** `ObjectOptimisticLockingFailureException` → 409 핸들러 등록 확인
- [ ] **[예방]** 모든 API 엔드포인트에 `@PreAuthorize` 적용 확인 (인가 없는 엔드포인트 0개)
- [ ] **[예방]** 도메인 팩토리 내 검증이 `create()` 호출 이전에 실행됨 — 서비스 레이어 테스트 검증

---

## Phase 2: AuditLog & Hash Chain (audit 모듈)

**목표**: 이후 모든 데이터 변경에 자동으로 AuditLog가 기록되는 AOP 인프라 구축

> esg-t1 BUG-P6-02 교훈: AuditTrail 누락 → @Auditable AOP로 선제 차단

### 작업 목록

#### DB

- [ ] V5__audit_tables.sql (`audit_logs`, `outbox_events`)

#### AOP 인프라

- [ ] `@Auditable` 어노테이션 정의
- [ ] `AuditAspect` 구현 (Around advice, before/after 상태 캡처)
- [ ] DB Outbox Pattern 구현 (`outbox_events` 테이블 + 폴러)
- [ ] `@ApplicationModuleListener` 비동기 처리 (audit 모듈)

#### Hash Chain

- [ ] `HashChainCalculator` 도메인 서비스 (SHA-256)
- [ ] `AuditLog` 저장 시 이전 해시 조회 → 현재 해시 계산
- [ ] PESSIMISTIC_WRITE 락 (해시 계산 순서 보장)

#### 검증 스케줄러

- [ ] 매일 새벽 2시 전체 Hash Chain 재검증
- [ ] 불일치 시 알림 이벤트 발행

#### TDD

- [ ] `test:` @Auditable 적용 메서드 → AuditLog 자동 기록 검증
- [ ] `test:` @Auditable 미적용 메서드 → AuditLog 미기록 확인
- [ ] `test:` Hash Chain 위변조 탐지 (중간 항목 수정 시 이후 항목 무효)
- [ ] `test:` Outbox 폴러 실패 → 재시도 후 처리 완료

### 완료 기준 (DoD)

- [ ] @Auditable 어노테이션만 붙이면 AuditLog 자동 기록
- [ ] Hash Chain 위변조 탐지 테스트 통과
- [ ] Outbox 이벤트 처리 실패율 0% (재시도 메커니즘 포함)
- [ ] audit 모듈 ModularityTest 통과
- [ ] **[예방]** AuditLog 저장 시 `PESSIMISTIC_WRITE` 락 사용 확인 (`synchronized + @Transactional` 조합 없음)
- [ ] **[예방]** `canonicalPayload()` 단일 정적 메서드로 저장·검증 경로 동일 직렬화 확인
- [ ] **[예방]** Hash Chain 검증 스케줄러에 `zone = "Asia/Seoul"` + `@ConditionalOnProperty` 설정 확인

---

## Phase 3: GHG 배출계수 로더 & Scope 1/2 계산 엔진

**목표**: 배출계수 DB 구축 및 Scope 1·2 배출량 자동 계산

### 작업 목록

#### DB

- [ ] V6__emission_factor_tables.sql (`emission_factors`, `factor_versions`)
- [ ] V7__activity_emission_tables.sql (`activity_data`, `emission_records`)

#### 배출계수 로더

- [ ] YAML 파일 포맷 정의 (`resources/emission-factors/keei-2025.yaml`, `defra-2025.yaml`)
- [ ] `EmissionFactorLoader` — item-level 멱등 upsert 구현

> esg-t1 BUG-P5-07 교훈: 파일 레벨 스킵 → 항목 레벨 upsert로 해결

- [ ] `test:` 동일 파일 2회 로드 시 데이터 중복 없음 (멱등성)
- [ ] `test:` 배출계수 값 수정 후 재로드 → 올바르게 업데이트

#### Scope 1/2 계산 엔진

- [ ] `EmissionCalculator` 도메인 서비스 (순수 함수, 외부 의존 없음)
- [ ] `EmissionFactorResolver` 인터페이스 + DefaultImpl (country, category, year 매칭)
- [ ] Location-based Scope 2 계산
- [ ] Market-based Scope 2 계산 (잔류 계수 포함)
- [ ] GWP 가중치 적용 (CO2e 환산)

#### TDD

- [ ] `test:` Scope 1 연료 연소 계산 (경유 1톤 → CO2e)
- [ ] `test:` Scope 2 location-based 전력 소비 계산
- [ ] `test:` Scope 2 market-based 재생에너지 인증서 적용
- [ ] `test:` 배출계수 미존재 시 예외 처리

#### API

- [ ] `POST /api/v1/ghg/activity-data` — @Auditable 적용
- [ ] `POST /api/v1/ghg/calculate` (Scope 1/2)
- [ ] `GET /api/v1/ghg/emission-records`

### 완료 기준 (DoD)

- [ ] 배출계수 로더 멱등성 테스트 통과
- [ ] Scope 1/2 계산 정확도 테스트 ≥ 5개 케이스
- [ ] 모든 활동 데이터 입력 → @Auditable AuditLog 기록 확인
- [ ] ghg 모듈 ModularityTest 통과
- [ ] **[예방]** `EmissionCalculator`에서 `float`/`double` 사용 코드 없음 — `BigDecimal` 전수 확인
- [ ] **[예방]** `EmissionFactorResolver.resolveAt(category, date)` — 과거 산출 시점 배출계수 재현 테스트 (L-0-09)
- [ ] **[예방]** 배출계수 YAML 로더: `DataIntegrityViolationException` 발생 시 WARN 로그 + 계속 처리 (중단 없음)

---

## Phase 3-B: 증빙 파일 관리 & 단위 변환 & ESG 지표 마스터

**목표**: 공시 수치와 원시 증빙을 연결하는 추적 가능성 인프라 구축

### 작업 목록

#### 증빙 파일 (Evidence)

- [ ] Object Storage 연동 — `ObjectStorageGateway` 인터페이스 + MinIO(개발) 구현
- [ ] `test:` 파일 업로드 → SHA-256 검증 → 다운로드 왕복 테스트
- [ ] `feat:` `POST /api/v1/evidence` — 파일 업로드 (`DigestInputStream`으로 단일 I/O)
- [ ] `feat:` 활동 데이터 ↔ 증빙 파일 N:M 연결 (`activity_data_evidence`)
- [ ] `feat:` 증빙 없는 필수 데이터 포인트 경고 플래그

#### 단위 변환

- [ ] `test:` GJ → kWh, TJ → GJ, Mcal → GJ 변환 정확도
- [ ] `feat:` `UnitConverter` 도메인 서비스 (양방향 기준 단위 변환)
- [ ] `feat:` 활동 데이터 저장 시 원 단위 + 변환 단위 병행 저장

#### ESG 지표 마스터

- [ ] V8__evidence_tables.sql (`evidence_files`, `activity_data_evidence`)
- [ ] V9__indicator_tables.sql (`esg_indicators`, `unit_conversions`)
- [ ] S/G 기본 지표 초기 데이터 (여성 관리자 비율, 산재율, 이직률, 이사회 출석률)
- [ ] `feat:` KSSB 2 / ISSB S2 프레임워크 매핑 YAML 로더 (멱등 upsert)
- [ ] `feat:` Formula DSL 초기 로드 (Scope 1/2 산식 YAML)

### 완료 기준 (DoD)

- [ ] 활동 데이터 입력 → 증빙 첨부 → 증빙 다운로드 통합 테스트
- [ ] SHA-256 불일치 파일 업로드 → 거부 테스트
- [ ] 단위 변환 오차 ≤ 0.001% (BigDecimal 정밀도 검증)
- [ ] Formula DSL 로드 후 `test_cases` 전체 통과
- [ ] **[예방]** 경로 순회 공격 테스트: `../../../etc/passwd` → `EsgException(INVALID_FILE_PATH)` 반환
- [ ] **[예방]** 허용 목록 외 확장자(`.exe`, `.sh`) 업로드 → 거부 테스트
- [ ] **[예방]** `DigestInputStream` 단일 I/O 패턴 확인 (업로드 + SHA-256 동시 처리)
- [ ] **[예방]** 활동 데이터 삭제 시도 → 비활성화 처리 확인 (물리 삭제 없음)

---

## Phase 4: 다법인 연결 집계 엔진

**목표**: 3개 이상 법인의 Scope 1/2 배출량을 지분율로 연결 집계

### 작업 목록

- [ ] `ConsolidationEngine` 도메인 서비스
  - Equity Method: `ConsolidatedEmission = Σ(EntityEmission × OwnershipRatio)`
  - Operational Control Method: 운영 지배력 100% 포함
  - 이중 계상 제거 알고리즘

#### TDD

- [ ] `test:` 3법인 연결 — Equity Method 지분율 적용 정확도
- [ ] `test:` 순환 지분 구조 탐지 → 예외 발생
- [ ] `test:` 이중 계상 제거 (A→B→C 체인에서 중간 법인 배출 중복 방지)
- [ ] `test:` 연결 경계 변경 (지분율 수정) → 재계산 일관성

#### API

- [ ] `GET /api/v1/entities/{id}/consolidated` (연결 집계 결과)
- [ ] `GET /api/v1/entities/{id}/consolidated?view=individual` (법인별 뷰)

#### DB

- [ ] V10__consolidated_emission_records.sql

### 완료 기준 (DoD)

- [ ] 3법인 연결 시나리오 전체 통합 테스트 통과
- [ ] 이중 계상 제거 케이스 단위 테스트 통과
- [ ] 연결 vs 개별 뷰 전환 API 정상 동작

---

## Phase 5: Scope 3 계산 엔진 (Category 1·2·11)

**목표**: GHG Protocol Phase 1 Update(2026-03) 요건 충족 Scope 3 계산 구현

### 작업 목록

#### Scope 3 Cat.1 — 구매재화·서비스 (지출 기반)
- [ ] `Scope3Cat1Calculator`: 지출액 × 지출 기반 배출계수
- [ ] 데이터 품질 점수 자동 부여 (SPEND_BASED/AVERAGE_DATA/SUPPLIER_SPECIFIC)
- [ ] `test:` 지출액 → CO2e 환산 정확도

#### Scope 3 Cat.2 — 자본재
- [ ] `Scope3Cat2Calculator`: 자본재 취득액 × LCA 계수
- [ ] `test:` 취득 시점 기준 배출 귀속 처리

#### Scope 3 Cat.11 — 판매제품 사용
- [ ] `Scope3Cat11Calculator`: 판매량 × 사용 단계 배출계수 × 예상 사용 기간
- [ ] `test:` 다년도 사용 배출량 현재연도 귀속

#### Scope 3 95% 커버리지 보고서

- [ ] `Scope3CoverageCalculator`: 포함/제외 카테고리 분류 + 비율 계산
- [ ] `scope3_coverage_report` 자동 생성 API
- [ ] `test:` 포함 카테고리 배출 합계 ÷ 총 추정 배출 ≥ 95% 판단

#### Category 16 사전 설계

- [ ] DB 스키마에 `scope3_category = 16` 허용 (계산 로직 제외)
- [ ] TODO 주석으로 2027년 구현 예정 표시

#### API

- [ ] `GET /api/v1/ghg/scope3-coverage-report`
- [ ] `POST /api/v1/ghg/calculate?scope=SCOPE3`

### 완료 기준 (DoD)

- [ ] Cat.1/2/11 계산 단위 테스트 각 ≥ 3개
- [ ] 95% 커버리지 보고서 생성 통합 테스트
- [ ] Category 16 스키마 유효성 검증 (값 저장은 가능, 계산 미구현)
- [ ] 데이터 품질 점수 표시 API 정상 동작

---

## Phase 6: 데이터 수집 파이프라인

**목표**: 수동 입력 외 CSV 업로드, API 연계, 공급업체 포털 구축

### 작업 목록

#### CSV/Excel 업로드

- [ ] 컬럼 매핑 설정 UI 지원 (프론트엔드 Phase 9에서)
- [ ] 백엔드: CSV 파싱 + 유효성 검사 + item-level 중복 방지
- [ ] 오류 행 리포트 반환

#### API 연계 (ERP 웹훅)

- [ ] Webhook 수신 엔드포인트 (`POST /api/v1/intake/webhook`)
- [ ] Signature 검증 (HMAC-SHA256)
- [ ] 데이터 정규화 → ActivityData 변환 파이프라인

#### 공급업체 포털 (supply 모듈)

- [ ] SUPPLIER 역할 계정 초대 → 이메일 발송
- [ ] `POST /api/v1/supplier/activity-data` (자사 데이터만 입력 가능)
- [ ] 제출 → ESG_MANAGER 승인 워크플로우

#### 알림

- [ ] 미제출 법인 자동 리마인더 (Scheduled + 이메일)

### 완료 기준 (DoD)

- [ ] CSV 100행 업로드 → 중복 없이 처리 (멱등성 테스트)
- [ ] Webhook 시그니처 검증 실패 → 401 응답
- [ ] 공급업체 포털 접근 격리 (자사 데이터만 열람 테스트)
- [ ] supply 모듈 ModularityTest 통과
- [ ] **[예방]** CSV 대량 업로드: 중간 행 오류 발생 시 이전 행 결과 보존 확인 (`REQUIRES_NEW` 트랜잭션)
- [ ] **[예방]** 중복 항목 업로드 시 `DataIntegrityViolationException` → WARN 로그 + 계속 처리 (ERROR 발생 없음)
- [ ] **[예방]** `@Async` 메서드와 `@Transactional` 메서드가 별도 빈으로 분리됨 확인

---

## Phase 6-B: 정정·재공시 워크플로우 & Formula DSL 배포

**목표**: 데이터 정정 후 재계산까지 이어지는 안전한 수정 프로세스 구축

### 작업 목록

#### 정정·재공시

- [ ] `test:` 활동 데이터 정정 → 새 버전 INSERT (원본 불변 확인)
- [ ] `test:` 정정 사유 코드 누락 → 예외
- [ ] `feat:` `POST /api/v1/ghg/activity-data/{id}/correct` @Auditable
- [ ] `feat:` 정정 이벤트 → 연결 배출량 재산출 자동 트리거
- [ ] `feat:` GET /api/v1/ghg/activity-data/{id}/versions — 버전 이력 조회
- [ ] `feat:` 정정 전·후 수치 비교 API (`/diff?version1=N&version2=M`)

#### Formula DSL 배포 파이프라인

- [ ] `test:` Formula YAML 등록 → test_cases 모두 통과해야 활성화
- [ ] `test:` test_cases 실패 시 활성화 차단 확인
- [ ] `feat:` Formula 버전 관리 (활성/비활성 상태)
- [ ] `feat:` 산식 변경 → 영향받는 배출량 목록 자동 조회

### 완료 기준 (DoD)

- [ ] 정정 후 이전 버전 보존 + 새 버전 생성 통합 테스트
- [ ] 정정 → 재계산 자동 트리거 테스트
- [ ] Formula DSL 배포 게이트 테스트 (test_cases 실패 시 차단)

---

## Phase 7: 공시 보고서 생성 (rpt 모듈)

**목표**: KSSB 2 / ISSB S2 공시 보고서 자동 생성 및 PDF 출력

### 작업 목록

- [ ] `ReportBuilder` 도메인 서비스 (섹션별 공시 항목 조립)
- [ ] KSSB 2 지표·목표 섹션 (Scope 1·2·3 포함)
- [ ] 비교연도 YoY 자동 계산
- [ ] 보고서 승인 워크플로우 (DRAFT → SUBMITTED → APPROVED)
- [ ] PDF 렌더링 (Apache PDFBox 또는 Jasper Reports)
- [ ] iXBRL XBRL taxonomy 매핑 데이터 모델 (렌더링 M+1)

#### API

- [ ] `POST /api/v1/reports` 보고서 생성
- [ ] `POST /api/v1/reports/{id}/approve` 승인
- [ ] `GET /api/v1/reports/{id}/pdf` PDF 다운로드

#### TDD

- [ ] `test:` Scope 1·2·3 합산 보고서 수치 정확도
- [ ] `test:` YoY 비교 계산 (전년 데이터 없을 경우 N/A 처리)
- [ ] `test:` DRAFT 상태 보고서 → 미승인 시 검증 스냅샷 생성 불가

### 완료 기준 (DoD)

- [ ] KSSB 2 필수 공시 항목 100% 커버 확인
- [ ] 보고서 생성 → PDF 다운로드 통합 테스트
- [ ] rpt 모듈 ModularityTest 통과
- [ ] **[예방]** 승인 상태 전이: `approve()`, `reject(reason)`, `escalate()` 메서드만 허용 — `setStatus()` 직접 호출 없음
- [ ] **[예방]** `reject(reason)` 호출 시 reason 공백 → `EsgException(REJECTION_REASON_REQUIRED)` 테스트
- [ ] **[예방]** YoY 비교 계산 — 전년 데이터 없을 경우 오류 없이 N/A 처리 확인

---

## Phase 8: 외부 검증 워크스페이스 (vw 모듈)

**목표**: 외부 감사인 전용 불변 스냅샷·검증 의견 시스템 구축

### 작업 목록

- [ ] `VerificationSnapshot` 도메인 (SHA-256 해시 + JSONB 불변 복사)
- [ ] 스냅샷 생성: 승인된 보고서만 대상 (APPROVED 상태 강제)
- [ ] PostgreSQL 트리거: 스냅샷 테이블 UPDATE/DELETE 차단
- [ ] VERIFIER 역할 RLS: 지정 스냅샷 외 접근 불가
- [ ] 검증 의견(코멘트) CRUD
- [ ] 검증 완료 서명 (`POST /api/v1/vw/snapshots/{id}/sign`)
- [ ] 검증인 모든 행동 @Auditable 기록

#### TDD

- [ ] `test:` 스냅샷 생성 후 원본 보고서 수정 → 스냅샷 불변 확인
- [ ] `test:` VERIFIER 역할 → 지정 스냅샷 외 데이터 접근 시 403
- [ ] `test:` 미승인 보고서로 스냅샷 생성 시도 → 예외

### 완료 기준 (DoD)

- [ ] 스냅샷 불변성 테스트 통과 (DB 트리거 + 도메인 레벨 이중 방어)
- [ ] VERIFIER 격리 통합 테스트 통과
- [ ] vw 모듈 ModularityTest 통과
- [ ] 검증인 온보딩 ~ 서명까지 E2E 시나리오 동작 확인

---

## Phase 9: 프론트엔드 — 기반 & 데이터 입력 UI

**목표**: 인증, 법인 관리, 활동 데이터 입력 화면 구축

### 작업 목록

- [ ] Next.js 15 App Router 기반 레이아웃 (사이드바 네비게이션)
- [ ] 로그인 / 토큰 갱신 / 로그아웃 플로우
- [ ] 법인 관리 UI (트리 시각화, 지분율 설정)
- [ ] 활동 데이터 입력 폼 (Scope 1·2·3 카테고리 선택)
- [ ] CSV 업로드 UI (컬럼 매핑, 오류 행 표시)
- [ ] 데이터 승인 워크플로우 UI (담당자 → 관리자)
- [ ] 역할별 메뉴 분기 (RBAC 기반 가시성)

### 완료 기준 (DoD)

- [ ] 3개 법인 등록 → 계층 트리 조회 UI 정상
- [ ] 활동 데이터 입력 → 저장 → 목록 조회 정상
- [ ] CSV 100행 업로드 → 오류 행 UI 표시 확인
- [ ] ESG_VIEWER 로그인 → 입력 버튼 비표시 확인

---

## Phase 10: 프론트엔드 — GHG 대시보드 & 보고서 UI

**목표**: 배출량 시각화, 보고서 생성·조회 화면 구축

### 작업 목록

- [ ] GHG 대시보드 (연도별·법인별·Scope별 배출량 차트)
- [ ] Scope 3 카테고리별 분류 차트
- [ ] Scope 3 95% 커버리지 보고서 UI (포함/제외 카테고리 시각화)
- [ ] 연결 vs 법인별 뷰 전환 토글
- [ ] 보고서 생성 위저드 (프레임워크 선택 → 데이터 확인 → 생성)
- [ ] PDF 다운로드 버튼
- [ ] 보고서 승인 워크플로우 UI

### 완료 기준 (DoD)

- [ ] Scope 1·2·3 합산 대시보드 차트 정상 렌더링
- [ ] KSSB 2 보고서 생성 → PDF 다운로드 정상
- [ ] 연결·개별 법인 뷰 전환 정상

---

## Phase 11: 프론트엔드 — 검증 워크스페이스 UI & 공급업체 포털

**목표**: 외부 검증인 전용 UI 및 공급업체 데이터 제출 포털

### 작업 목록

#### 검증 워크스페이스 (VERIFIER 전용)

- [ ] 스냅샷 목록 조회 (지정된 스냅샷만 표시)
- [ ] 스냅샷 상세 조회 (배출량·증거 문서·코멘트)
- [ ] 코멘트 작성 UI
- [ ] 검증 완료 서명 버튼 + 확인 다이얼로그

#### 공급업체 포털 (SUPPLIER 전용)

- [ ] 초대 링크 → 계정 설정 온보딩
- [ ] 구매 카테고리별 활동 데이터 입력
- [ ] 제출 현황 조회

#### AuditLog UI (TENANT_ADMIN 전용)

- [ ] 날짜·엔티티 필터 조회
- [ ] Hash Chain 무결성 보고서 조회

### 완료 기준 (DoD)

- [ ] VERIFIER 로그인 → 지정 스냅샷 외 접근 불가 UI 확인
- [ ] 공급업체 데이터 제출 → ESG_MANAGER 승인 플로우 E2E 확인
- [ ] AuditLog 조회 → 변경 이력 표시 확인

---

## Phase 12: 통합 검증 & 성능 최적화 & 보안 감사

**목표**: 출시 전 품질 기준 달성

### 작업 목록

#### 성능 테스트

- [ ] Virtual Threads 활성화 후 Scope 3 배치 계산 (100개 공급업체) 30초 이내 확인
- [ ] P95 API 응답시간 500ms 이하 달성 (부하 테스트: k6)
- [ ] 동시 사용자 100명 부하 테스트

#### 보안 감사

- [ ] OWASP ZAP 자동 스캔
- [ ] SQL Injection 방어 검증 (PreparedStatement 전수 확인)
- [ ] IDOR 취약점 — 다른 테넌트 데이터 접근 시도 테스트
- [ ] JWT 탈취 시나리오 (블랙리스트 로그아웃 검증)
- [ ] RLS 우회 시도 테스트

#### 정합성 검증

- [ ] KSSB 2 필수 공시 항목 체크리스트 전수 확인
- [ ] Scope 3 95% 임계값 보고서 계산 정확도 재검증
- [ ] Hash Chain 무결성 검증기 24시간 운영 후 이상 없음 확인

#### 문서화

- [ ] API 문서 (OpenAPI 3.1) 최종 검토
- [ ] `docs/insight.md` 주요 교훈 정리
- [ ] `docs/adr/` ADR 최종 검토

### 완료 기준 (DoD)

- [ ] 전체 테스트 스위트 Green (단위 + 통합 + 모듈)
- [ ] P95 응답시간 ≤ 500ms (일반), ≤ 1,500ms (보고서 생성)
- [ ] OWASP ZAP 스캔 High/Critical 취약점 0건
- [ ] **[예방]** `EmissionCalculator` 전체 코드에서 `float`/`double` 없음 확인 (grep 검증)
- [ ] **[예방]** Formula DoS 한계값 초과 공격 테스트 (depth 51 수식 → `FormulaValidationException` 반환)
- [ ] **[예방]** 통합 테스트에서 Hibernate statistics로 N+1 쿼리 없음 검증 (주요 조회 API 3개 이상)
- [ ] Scope 3 배치 처리 ≤ 30초 (100개 공급업체)
- [ ] KSSB 2 필수 항목 커버 100%
- [ ] AuditLog 누락 건수 0건 (전체 데이터 변경 재검증)

---

## M+1 계획 (MVP 이후)

| 기능 | 우선순위 | 의존 |
|---|---|---|
| 이중 중대성 평가(DMA) | P0 | CSRD 대응 기업 니즈 |
| GRI / SASB 보고서 | P0 | 프레임워크 확장 |
| iXBRL 렌더링 엔진 | P1 | DART 제출 준비 |
| AI 데이터 검증 보조 | P1 | 이상값 탐지 |
| 기후 시나리오 분석 | P2 | IFRS S2 전략 섹션 |
| CDP 질문지 자동 매핑 | P2 | 자발적 공시 지원 |
| 공급업체 리스크 스코어 | P2 | Scope 3 신뢰도 |
| Scope 3 Category 16 계산 | P3 | GHG Protocol 2027 확정 후 |

---

## 의존성 그래프

```
Phase 0 (기반)
  └── Phase 1 (법인)
        ├── Phase 2 (AuditLog) ← 이후 모든 Phase 의존
        │     └── Phase 3 (Scope 1/2)
        │           └── Phase 4 (연결 집계)
        │                 └── Phase 5 (Scope 3)
        │                       └── Phase 6 (데이터 수집)
        │                             └── Phase 7 (보고서)
        │                                   └── Phase 8 (검증 WS)
        │                                         └── Phase 11 (VW UI)
        └── Phase 9 (기본 UI)
              └── Phase 10 (GHG 대시보드 UI)
                    └── Phase 11 (검증/공급업체 UI)
                          └── Phase 12 (통합 검증)
```

---

## 리스크 관리

| 리스크 | 영향 | 대응 |
|---|---|---|
| KSSB 2 세부 가이던스 추가 발표 | 보고서 섹션 구조 변경 | 보고서 컨텐츠 JSONB로 유연하게 저장 |
| GHG Protocol Category 16 조기 확정 | Scope 3 계산 엔진 추가 작업 | 데이터 모델 사전 준비로 충격 최소화 |
| Scope 3 공급업체 데이터 수집 저조 | Cat.1 정확도 하락 | 지출 기반 → 공급업체 특정 전환 경로 제공 |
| ecoinvent 라이선스 지연 | LCA 데이터 대체 방법 | DEFRA 대체 계수 먼저 사용 |
| 법인 계층 순환 참조 | ConsolidationEngine 무한 루프 | Phase 1에서 DAG 검증 로직 선제 구축 |

---

## 검토 이력

| 날짜 | 검토자 | 버전 | 주요 변경 |
|---|---|---|---|
| 2026-05-18 | Claude Code (esg-t2 기획) | 1.0 | 초안 작성 |
