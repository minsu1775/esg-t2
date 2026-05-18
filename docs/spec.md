# ESG 공시지원 시스템 esg-t2 — 기술 명세서 (Spec)

> **버전**: 1.0  
> **작성일**: 2026-05-18  
> **레퍼런스**: prd.md v1.0, esg-t1/docs/spec.md v0.1, CLAUDE.md, docs/superpowers/plans/2026-05-18-esg-t2-implementation.md  
> **기술 스택**: Spring Boot 4.0.x / Java 25 / Next.js 15 / PostgreSQL 18

---

## 1. 기술 스택 (Tech Stack)

### 1.1 백엔드

| 구성요소 | 버전 | 선택 근거 |
|---|---|---|
| Java | 25 LTS | Virtual Threads (Project Loom), JSpecify null-safety |
| Spring Boot | 4.0.x | Java 25 first-class 지원, OpenTelemetry 내장, GraalVM native |
| Spring Modulith | 2.0.x | `@ApplicationModule` 컴파일 타임 경계 강제 (Spring Boot 4.0 BOM) |
| Spring Security | 7.x | JWT + RBAC (Spring Framework 7 기반) |
| Spring Data JPA | 4.x | PostgreSQL 18 연동, Auditing (Spring Framework 7 기반) |
| Flyway | 11.x | DB 마이그레이션 |
| Testcontainers | 1.21.x | 통합 테스트용 PostgreSQL |
| Gradle | 8.12+ | Kotlin DSL 빌드 스크립트 |

### 1.2 프론트엔드

| 구성요소 | 버전 | 선택 근거 |
|---|---|---|
| Next.js | 15 | App Router, React Server Components |
| React | 19 | Server Actions, Suspense |
| TypeScript | 5.6+ | strict 모드 |
| Tailwind CSS | 4.x | 유틸리티 퍼스트 |
| Tanstack Query | 5.x | 서버 상태 관리 |
| Recharts | 2.x | ESG 시각화 차트 |
| Zod | 3.x | 입력 유효성 검사 |

### 1.3 인프라 / 관측성

| 구성요소 | 목적 |
|---|---|
| PostgreSQL 18 | Primary DB (Row-Level Security) |
| Redis | JWT 블랙리스트, 캐시 |
| OpenTelemetry (OTLP) | 분산 트레이싱 (Spring Boot 4 내장) |
| Prometheus + Grafana | 메트릭 수집·시각화 |
| Docker Compose | 로컬 개발 환경 |

---

## 2. 아키텍처 개요

### 2.1 레이어 구조

```
┌────────────────────────────────────────────────────┐
│                 Frontend (Next.js 15)               │
│  Pages · Components · Server Actions · API Routes  │
└────────────────────┬───────────────────────────────┘
                     │ HTTPS / REST
┌────────────────────▼───────────────────────────────┐
│           Backend (Spring Boot 4 / Java 25)         │
│                                                    │
│  ┌─────────────────────────────────────────────┐  │
│  │           Spring Modulith Modules            │  │
│  │  ghg │ entity │ vw │ rpt │ supply │ audit   │  │
│  └──────────────────────┬──────────────────────┘  │
│                         │ Spring Events (비동기)    │
│  ┌──────────────────────▼──────────────────────┐  │
│  │           Domain Layer (순수 Java)            │  │
│  │  EmissionCalculator · ConsolidationEngine    │  │
│  │  Scope3CoverageCalculator · HashChainVerifier│  │
│  └──────────────────────┬──────────────────────┘  │
│                         │                          │
│  ┌──────────────────────▼──────────────────────┐  │
│  │          Infrastructure Layer               │  │
│  │  JPA Repositories · Redis · SMTP · Outbox   │  │
│  └─────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
                     │
┌────────────────────▼───────────────────────────────┐
│               PostgreSQL 17 (RLS 활성화)             │
└────────────────────────────────────────────────────┘
```

### 2.2 Spring Modulith 모듈 경계

```
ai.claudecode.esgt2
├── ghg/          # GHG 배출량 계산 엔진 (Scope 1·2·3)
├── entity/       # 법인·테넌트 관리, 연결 경계
├── vw/           # Verification Workspace (외부 검증인)
├── rpt/          # 보고서 생성 (KSSB, ISSB, PDF)
├── supply/       # 공급업체 포털, Scope 3 Cat.1 데이터 수집
├── audit/        # AuditLog, Hash Chain, 이상 징후 탐지
└── shared/       # 공통 Value Objects, Events, Exceptions
```

**모듈 간 통신 규칙**:
- 동기 호출: 공개 API(`public` package) 인터페이스만 허용
- 비동기 호출: `ApplicationEventPublisher` → `@ApplicationModuleListener`
- 직접 Repository 크로스 참조: 금지 (`ModularityTest`에서 강제)

### 2.3 Domain ≠ Entity 원칙 (esg-t1에서 계승)

```java
// ✅ 올바른 패턴: 도메인 팩토리를 통한 생성
public EmissionRecordId create(CreateEmissionCommand cmd) {
    EmissionRecord domain = EmissionRecord.create(cmd); // 도메인 팩토리
    EmissionRecordJpaEntity entity = mapper.toEntity(domain);
    return EmissionRecordId.of(repository.save(entity).getId());
}

// ❌ 금지: JPA Entity 직접 빌더 사용
EmissionRecordJpaEntity entity = EmissionRecordJpaEntity.builder()...build(); // 금지
```

---

## 3. 데이터 모델 (DB Schema)

### 3.1 핵심 테이블 정의

#### tenants (테넌트)
```sql
CREATE TABLE tenants (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50) NOT NULL UNIQUE,  -- 테넌트 식별 코드 (예: SAMSUNG, DEMO)
    name         VARCHAR(200) NOT NULL,
    country_code CHAR(2)     NOT NULL,         -- 본사 소재 국가 (ISO 3166-1)
    plan_type    VARCHAR(50) NOT NULL DEFAULT 'ENTERPRISE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ
);
```

#### legal_entities (법인)
```sql
CREATE TABLE legal_entities (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id),
    name         VARCHAR(200) NOT NULL,
    country_code CHAR(2)     NOT NULL,
    entity_type  VARCHAR(30) NOT NULL,  -- PARENT, SUBSIDIARY, ASSOCIATE
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    UNIQUE(tenant_id, name)
);

-- RLS 정책은 db/migration-pg/ 에 위치 (PostgreSQL 전용 DDL 분리 원칙)
-- db/migration-pg/V3__rls_policies.sql:
--   ALTER TABLE legal_entities ENABLE ROW LEVEL SECURITY;
--   CREATE POLICY tenant_isolation ON legal_entities
--       USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

#### entity_relationships (법인 간 관계)
```sql
CREATE TABLE entity_relationships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    parent_id       UUID NOT NULL REFERENCES legal_entities(id),
    child_id        UUID NOT NULL REFERENCES legal_entities(id),
    ownership_ratio NUMERIC(5,4) NOT NULL, -- 0.0000 ~ 1.0000
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    CHECK (ownership_ratio BETWEEN 0 AND 1),
    CHECK (parent_id != child_id)
);
```

#### activity_data (활동 데이터)
```sql
CREATE TABLE activity_data (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    entity_id       UUID NOT NULL REFERENCES legal_entities(id),
    reporting_year  INT NOT NULL,
    category        VARCHAR(50) NOT NULL, -- SCOPE1_FUEL, SCOPE2_ELECTRICITY, SCOPE3_CAT1 ...
    sub_category    VARCHAR(100),
    quantity        NUMERIC(20, 6) NOT NULL,
    unit            VARCHAR(30) NOT NULL,
    data_source     VARCHAR(50) NOT NULL, -- MANUAL, CSV_UPLOAD, API, SUPPLIER_PORTAL
    data_quality    VARCHAR(20) NOT NULL DEFAULT 'SPEND_BASED', -- SPEND_BASED, AVERAGE_DATA, SUPPLIER_SPECIFIC
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, APPROVED, REJECTED
    submitted_by    UUID,
    approved_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### emission_records (배출량 계산 결과)
```sql
CREATE TABLE emission_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    entity_id           UUID NOT NULL REFERENCES legal_entities(id),
    activity_data_id    UUID REFERENCES activity_data(id),
    reporting_year      INT NOT NULL,
    scope               VARCHAR(10) NOT NULL, -- SCOPE1, SCOPE2_LB, SCOPE2_MB, SCOPE3
    scope3_category     INT, -- 1~15, 16 (null if not scope3)
    ghg_type            VARCHAR(20) NOT NULL DEFAULT 'CO2E',
    emission_factor_id  UUID NOT NULL REFERENCES emission_factors(id),
    raw_emission        NUMERIC(20, 6) NOT NULL, -- tCO2e (법인 개별)
    consolidated_share  NUMERIC(20, 6), -- 연결 지분율 적용 후
    is_consolidated     BOOLEAN NOT NULL DEFAULT FALSE,
    calculated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### emission_factors (배출계수 마스터)
```sql
CREATE TABLE emission_factors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source          VARCHAR(50) NOT NULL, -- KEEI, DEFRA, IPCC_AR6
    category        VARCHAR(100) NOT NULL,
    sub_category    VARCHAR(100),
    country_code    CHAR(2),
    reporting_year  INT NOT NULL,         -- 'year' 예약어 사용 금지 → reporting_year
    gwp_source      VARCHAR(30) NOT NULL DEFAULT 'IPCC_AR6', -- GWP 기준
    factor_value    NUMERIC(20, 8) NOT NULL,
    unit            VARCHAR(50) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (source, category, sub_category, country_code, reporting_year)
);
```

#### scope3_coverage_reports (Scope 3 95% 임계값 보고서)
```sql
CREATE TABLE scope3_coverage_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    entity_id       UUID NOT NULL,
    reporting_year  INT NOT NULL,
    total_categories INT NOT NULL DEFAULT 16,
    included_categories INT[] NOT NULL,
    excluded_categories INT[],
    exclusion_reasons JSONB, -- {category: reason}
    coverage_pct    NUMERIC(5, 2) NOT NULL,
    meets_95pct_threshold BOOLEAN NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### disclosure_reports (공시 보고서)
```sql
CREATE TABLE disclosure_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    entity_id       UUID NOT NULL,
    reporting_year  INT NOT NULL,
    framework       VARCHAR(30) NOT NULL, -- KSSB2, ISSB_S2, GRI (M+1)
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content         JSONB NOT NULL, -- 섹션별 공시 내용
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    approved_by     UUID
);
```

#### verification_snapshots (검증 스냅샷 — VW 모듈)
```sql
CREATE TABLE verification_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL REFERENCES disclosure_reports(id),
    snapshot_hash   VARCHAR(64) NOT NULL, -- SHA-256 of content
    snapshot_data   JSONB NOT NULL, -- 불변 복사본
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    frozen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

스냅샷 불변성 보장 전략 (2중 방어):
1. **DB 권한 박탈** (`db/migration-pg/` 에 위치 — PostgreSQL 전용):
   ```sql
   REVOKE UPDATE, DELETE ON verification_snapshots FROM app_user;
   ```
2. **도메인 레벨**: `VerificationSnapshot.create()` 팩토리만 허용, 변경 메서드 미노출

> ⚠️ `CREATE RULE ... DO INSTEAD NOTHING` 방식은 PostgreSQL 레거시 기능으로 오류를 던지지 않아 조용히 무시됨. 사용 금지.

#### audit_logs (감사 이력 — Hash Chain)
```sql
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID,         -- NULL 허용: 시스템 레벨 이벤트(테넌트 생성, 스케줄러 등)
    event_type      VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    actor_id        UUID NOT NULL,
    actor_role      VARCHAR(50) NOT NULL,
    before_value    JSONB,
    after_value     JSONB,
    metadata        JSONB,
    previous_hash   VARCHAR(64), -- 이전 항목의 hash
    current_hash    VARCHAR(64) NOT NULL, -- SHA-256(previous_hash + payload)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### evidence_files (증빙 파일)
```sql
CREATE TABLE evidence_files (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    storage_uri VARCHAR(500) NOT NULL, -- Object Store 경로
    sha256      CHAR(64) NOT NULL,     -- 파일 무결성 검증
    filename    VARCHAR(200) NOT NULL,
    mime_type   VARCHAR(100),
    size_bytes  BIGINT,
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 활동 데이터와 N:M (동일 증빙 파일을 여러 데이터 포인트에 첨부 가능)
CREATE TABLE activity_data_evidence (
    activity_data_id UUID NOT NULL REFERENCES activity_data(id),
    evidence_id      UUID NOT NULL REFERENCES evidence_files(id),
    PRIMARY KEY (activity_data_id, evidence_id)
);
```

#### esg_indicators (ESG 지표 마스터 — S/G 포함)
```sql
CREATE TABLE esg_indicators (
    code            VARCHAR(50) PRIMARY KEY,  -- 예: EM-S1-FUEL, HR-TURNOVER
    name_ko         VARCHAR(200) NOT NULL,
    category        VARCHAR(5) NOT NULL,       -- E / S / G
    data_type       VARCHAR(20) NOT NULL,      -- QUANTITATIVE / QUALITATIVE / NARRATIVE
    unit            VARCHAR(30),               -- 정성/서술형은 NULL
    sensitivity     VARCHAR(20) NOT NULL DEFAULT 'INTERNAL', -- PUBLIC/INTERNAL/RESTRICTED/CONFIDENTIAL
    framework_codes TEXT[],                    -- 매핑된 프레임워크 (KSSB2, GRI 등)
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);
```

#### unit_conversions (단위 변환 마스터)
```sql
CREATE TABLE unit_conversions (
    from_unit   VARCHAR(30) NOT NULL,
    to_unit     VARCHAR(30) NOT NULL,
    factor      NUMERIC(20, 10) NOT NULL, -- to_unit = from_unit × factor
    category    VARCHAR(50),              -- ENERGY / MASS / VOLUME
    PRIMARY KEY (from_unit, to_unit)
);
-- 예시: GJ → kWh × 277.778, TJ → GJ × 1000, Mcal → GJ × 0.004187
```

#### disclosure_schedules (규제 일정 — 하드코딩 금지)
```sql
CREATE TABLE disclosure_schedules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction        VARCHAR(20) NOT NULL, -- KR, EU, GLOBAL
    framework           VARCHAR(30) NOT NULL, -- KSSB2, CSRD, ISSB_S2
    entity_criteria     VARCHAR(200), -- "연결자산 30조 이상"
    mandatory_from_year INT NOT NULL,
    scope3_from_year    INT,
    notes               TEXT,
    source_document     VARCHAR(200),
    last_updated        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 3-A. Object Storage (증빙 파일 저장소)

증빙 파일은 DB가 아닌 별도 Object Storage에 저장한다 (esg-t1 PRD §A.4 원칙 계승).

| 환경 | 저장소 | 비고 |
|---|---|---|
| 로컬 개발 | MinIO (Docker Compose) | S3 호환 API |
| 운영 | AWS S3 또는 동등 S3 호환 | 버전 관리 + 수명주기 정책 |

```java
// ObjectStorageGateway.java (interface — 환경별 구현체 교체)
public interface ObjectStorageGateway {
    StorageUri upload(InputStream stream, String filename, String mimeType);
    InputStream download(StorageUri uri);
    void delete(StorageUri uri); // 물리 삭제는 비활성화 처리로 대체
}
// 업로드 시 SHA-256 동시 계산: DigestInputStream 패턴 (I/O 1회)
```

---

## 3-B. Formula DSL (산식 정의 YAML)

산식은 코드가 아닌 YAML로 정의한다. 보안 평가기(`CustomExpressionEvaluator`)가 화이트리스트 함수만 허용한다.

```yaml
# resources/formulas/em_s1_fuel.yaml
formula:
  code: EM-S1-FUEL
  name: 연료 연소 직접배출 (Scope 1)
  version: "1.0"
  inputs:
    - var: fuel_consumption
      indicator: ENERGY-FUEL-USE
      unit: GJ
    - var: ef
      lookup: emission_factor
      key: fuel_type
      gwp: AR6       # IPCC AR6 GWP 기준
  expression: "fuel_consumption * ef * 0.001"   # tCO2e
  output_unit: tCO2e
  test_cases:
    - inputs: { fuel_consumption: 1000.0, ef: 56.3 }
      expected: 56.3
    - inputs: { fuel_consumption: 0.0, ef: 56.3 }
      expected: 0.0
```

**평가기 보안 규칙**:
- 허용 연산자: `+ - * / ( )`
- 허용 함수: `abs min max sum if pow log`
- 금지: `T(...)`, `new`, SpEL 메서드 호출, Reflection, `eval`-style 임의 코드
- **새 산식 등록 시 `test_cases` 비어 있으면 퍼블리시 거부** — 검증 없는 산식 활성화 차단
- DoS 방어: `MAX_EXPRESSION_LENGTH=1000`, `MAX_NUMBER_LENGTH=50`, `MAX_PARSER_DEPTH=50`, `MAX_EVAL_DEPTH=50` (FormulaConstants 상수)
- 한계값 초과 시 `FormulaValidationException`(400) 반환 — 서버 스택 오버플로 없음

```yaml
# resources/frameworks/kssb2.yaml — 프레임워크 매핑
framework: KSSB-2
version: "2026-02"
items:
  - item_code: KSSB2.S1
    title: Scope 1 직접 배출량
    indicators: [EM-S1-FUEL, EM-S1-PROCESS, EM-S1-FUGITIVE]
    aggregation: sum
    required: true
  - item_code: KSSB2.S3-CAT1
    title: Scope 3 Cat.1 구매재화·서비스
    indicators: [EM-S3-CAT1]
    aggregation: sum
    required: true
```

---

## 4. 모듈별 상세 설계

### 4.1 ghg 모듈 — GHG 배출량 계산 엔진

**패키지 구조**:
```
ghg/
├── api/
│   ├── GhgCalculationService.java   # 퍼블릭 API (다른 모듈 호출 가능)
│   └── dto/                         # Command / Result 객체
├── domain/
│   ├── EmissionRecord.java          # 도메인 객체
│   ├── EmissionCalculator.java      # 계산 로직 (순수 함수)
│   ├── ConsolidationEngine.java     # 연결 집계 엔진
│   ├── Scope3CoverageCalculator.java
│   └── EmissionFactorResolver.java  # 배출계수 조회 (interface)
├── infra/
│   ├── EmissionRecordJpaRepository.java
│   ├── EmissionFactorJpaRepository.java
│   └── DefaultEmissionFactorResolver.java
└── internal/
    └── GhgApplicationService.java   # @ApplicationModule 내부 전용
```

**핵심 계산 흐름**:
```
ActivityData → EmissionFactorResolver → EmissionCalculator
→ EmissionRecord (법인별)
→ ConsolidationEngine (지분율 적용)
→ ConsolidatedEmissionRecord (연결 기준)
```

**Scope 3 Cat.1 계산 (지출 기반)**:
```
Emission(tCO2e) = Spend(KRW) × SpendBasedFactor(tCO2e/KRW) × ExchangeRateAdj
```

**연결 배출량 (equity method)**:
```
ConsolidatedEmission = Σ (EntityEmission × OwnershipRatio)
```

### 4.2 entity 모듈 — 법인 관리

**핵심 도메인 객체**:

```java
public record LegalEntity(
    LegalEntityId id,
    TenantId tenantId,
    String name,
    CountryCode countryCode,
    int reportingYear,
    ConsolidationMethod consolidationMethod,
    boolean isParent
) {
    public static LegalEntity create(CreateEntityCommand cmd) {
        // 유효성 검사 포함 팩토리 메서드
    }
}
```

**연결 경계 계산**:
- `EntityRelationshipGraph`: 법인 트리를 DAG(Directed Acyclic Graph)로 표현
- 이중 계상 방지: 동일 법인이 복수 경로로 포함될 경우 최상위 경로 우선

### 4.3 vw 모듈 — Verification Workspace

**스냅샷 생성 프로세스**:
```
1. disclosure_reports에서 승인된 보고서 선택
2. 관련 emission_records, activity_data 전체 복사
3. SHA-256 해시 계산 (보고서 내용 + 타임스탬프)
4. verification_snapshots에 불변 저장
5. VERIFIER 계정에 접근 링크 발급
```

**검증인 접근 제어**:
- VERIFIER 역할 계정은 지정된 snapshot_id 외 테이블 접근 불가
- RLS 정책: `WHERE snapshot_id = current_setting('app.verifier_snapshot_id')`

### 4.4 audit 모듈 — AuditLog & Hash Chain

**@Auditable AOP**:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String entityType() default "";
}

// AuditAspect.java
@Around("@annotation(auditable)")
public Object audit(ProceedingJoinPoint pjp, Auditable auditable) {
    Object before = captureState(pjp);
    Object result = pjp.proceed();
    Object after = captureState(pjp);
    publishAuditEvent(auditable.action(), before, after);
    return result;
}
```

**Hash Chain 계산**:
```
currentHash = SHA-256(previousHash + eventType + entityId + actorId + timestamp + payload)
```

**Hash Chain 저장 — PESSIMISTIC_WRITE 락 필수 (esg-t1 L-0-04)**:
```java
// AuditLogRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<AuditLogEntity> findLatestByTenantId(UUID tenantId);
// 이유: synchronized + @Transactional 조합 시 트랜잭션 커밋 전 락 해제 → 레이스 컨디션
// 반드시 DB 레벨 PESSIMISTIC_WRITE로 트랜잭션 경계 내 순서 보장
```

**Canonical JSON — 동일 직렬화 경로 강제 (esg-t1 L-0-08)**:
```java
// HashChainCalculator.java
public static Map<String, Object> canonicalPayload(AuditEvent event) {
    // 단일 정적 메서드로 저장 경로·검증 경로 동일 직렬화 보장
    // 필드 순서·null 처리가 달라지면 해시 불일치 → 항상 무결성 오류
}
// 저장 시: canonicalPayload() → SHA-256
// 검증 시: canonicalPayload() → SHA-256 (동일 함수 호출 필수)
```

**무결성 검증 스케줄러**:
```java
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul") // 매일 새벽 2시 (KST)
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public void verifyHashChainIntegrity() {
    // 전체 audit_logs 순차 조회 → 해시 재계산 → 불일치 시 알림
}
```

---

## 5. API 명세 개요

> 상세 OpenAPI 스펙은 서버 기동 후 `/swagger-ui.html` 참조.

### 5.1 주요 엔드포인트

#### Entity Management
```
POST   /api/v1/entities                     # 법인 등록
GET    /api/v1/entities                     # 법인 목록
PUT    /api/v1/entities/{id}/relationships  # 지배·종속 관계 설정
GET    /api/v1/entities/{id}/consolidated   # 연결 배출량 조회
```

#### GHG Calculation
```
POST   /api/v1/ghg/activity-data           # 활동 데이터 입력
GET    /api/v1/ghg/activity-data           # 활동 데이터 목록 (필터링)
POST   /api/v1/ghg/calculate               # 배출량 계산 트리거
GET    /api/v1/ghg/emission-records        # 배출량 결과 조회
GET    /api/v1/ghg/scope3-coverage-report  # Scope 3 커버리지 보고서
```

#### Reports
```
POST   /api/v1/reports                     # 보고서 생성
GET    /api/v1/reports/{id}               # 보고서 조회
POST   /api/v1/reports/{id}/approve        # 보고서 승인
GET    /api/v1/reports/{id}/pdf            # PDF 다운로드
```

#### Verification Workspace
```
POST   /api/v1/vw/snapshots               # 검증 스냅샷 생성
GET    /api/v1/vw/snapshots/{id}          # 스냅샷 조회 (VERIFIER)
POST   /api/v1/vw/snapshots/{id}/comments # 검증 의견 작성
POST   /api/v1/vw/snapshots/{id}/sign     # 검증 완료 서명
```

#### Audit
```
GET    /api/v1/audit/logs                 # AuditLog 조회 (날짜, 엔티티 필터)
GET    /api/v1/audit/integrity-report     # Hash Chain 무결성 보고서
```

### 5.2 인증 플로우

**MVP 인증**: Spring Security OAuth2 Resource Server + 자체 JWT 발급

```
POST /api/v1/auth/login
  → { access_token (15분), refresh_token (7일) }

POST /api/v1/auth/refresh
  → { access_token (갱신) }

POST /api/v1/auth/logout
  → refresh_token Redis 블랙리스트 등록
```

> **M+1 업그레이드 경로**: Keycloak OIDC (SAML 2.0 + MFA TOTP) 도입.  
> esg-t1에서 Keycloak 통합 완료 검증. esg-t2 MVP는 구현 속도 우선으로 자체 JWT로 시작하되, `jwk-set-uri` 기반 `NimbusJwtDecoder`를 쓰면 Keycloak 전환 시 코드 변경 최소화.  
> JWT 역할 클레임 위치: `realm_access.roles` (Keycloak 표준 위치) 또는 커스텀 클레임 — `Converter<Jwt, Collection<GrantedAuthority>>` 구현으로 처리.

### 5.3 수치 → 증빙 역추적 (Evidence-to-Disclosure Traceability)

공시 보고서 수치에서 원시 증빙까지 3단계 이하로 추적 가능해야 한다:

```
공시 보고서 수치
  → EmissionRecord (배출량 계산 결과)
    → ActivityData (활동 데이터)
      → EvidenceFile (증빙 파일 다운로드)
```

PDF 보고서에는 수치 셀에 `evidence://{activity_data_id}` 내부 링크 임베드 (내부 검토용), 대외 공시용은 링크 제거.

---

## 6. 보안 모델

### 6.1 RBAC 매트릭스

| 기능 | SUPER_ADMIN | TENANT_ADMIN | ESG_MANAGER | ESG_VIEWER | VERIFIER | SUPPLIER |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| 테넌트 생성 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| 법인 관리 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| 활동 데이터 입력 | ✅ | ✅ | ✅ | ❌ | ❌ | 자사만 |
| 배출량 계산 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 보고서 조회 | ✅ | ✅ | ✅ | ✅ | 스냅샷만 | ❌ |
| 보고서 승인 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 스냅샷 생성 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 검증 서명 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| AuditLog 조회 | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

### 6.2 Row-Level Security (PostgreSQL RLS)

#### TenantContextInterceptor — 세션 변수 설정 (구현 필수)

매 요청마다 JWT에서 tenant_id를 추출해 PostgreSQL 세션 변수에 설정한다.

```java
// TenantContextInterceptor.java (Phase 1 구현)
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    @Autowired
    private DataSource dataSource;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object h) {
        String tenantId = SecurityContextHolder.getContext()
            .getAuthentication()./* JWT claim */.getTenantId();
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                "SET LOCAL app.current_tenant_id = '" + tenantId + "'");
        }
        return true;
    }
}
```

> ⚠️ 세션 변수 미설정 시 RLS가 동작하지 않아 전체 테이블 접근 가능. 반드시 인터셉터 등록 확인.

#### RLS 정책 (모든 핵심 테이블 적용)

```sql
-- 모든 핵심 테이블에 동일 패턴 적용
CREATE POLICY tenant_isolation ON emission_records
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

#### VERIFIER 전용 RLS 설계

VERIFIER는 테넌트 내에서도 지정된 snapshot_id 외 접근 불가. 두 세션 변수를 조합:

```sql
-- VERIFIER용 추가 정책
CREATE POLICY verifier_snapshot_isolation ON verification_snapshots
    FOR SELECT TO app_user
    USING (
        -- 일반 사용자: 테넌트 격리만
        current_setting('app.verifier_snapshot_id', true) IS NULL
        OR
        -- VERIFIER: 지정 스냅샷만
        id = current_setting('app.verifier_snapshot_id', true)::UUID
    );
```

VERIFIER 로그인 시 JWT claim에 `verifier_snapshot_id`를 포함하여 `TenantContextInterceptor`에서 `SET LOCAL app.verifier_snapshot_id = '...'` 추가 설정.

---

## 7. 테스트 전략

### 7.1 TDD 사이클 (강제)

```
Red   → 실패 테스트 작성 (커밋 prefix: test:)
Green → 최소 구현 (커밋 prefix: feat:)
Refactor → 리팩토링 (커밋 prefix: refactor:)
```

### 7.2 테스트 계층

| 계층 | 도구 | 대상 | 정책 |
|---|---|---|---|
| Unit | JUnit 5 + AssertJ | Domain 객체, 계산 엔진 | 외부 의존 없음, 빠른 피드백 |
| Integration | Testcontainers + PostgreSQL | Repository, Service | **Mock DB 금지** (esg-t1 교훈) |
| Module | `ModularityTest` | 모듈 경계 | 빌드 파이프라인에서 강제 |
| API | MockMvc / RestAssured | Controller | 인증 포함 E2E 시나리오 |
| E2E | Playwright | 핵심 User Journey | CI에서 선택적 실행 |

### 7.3 핵심 테스트 케이스

```java
// GHG 계산 정확도
@Test void scope1_emission_calculation_with_official_factor() { ... }
@Test void scope3_cat1_spend_based_calculation() { ... }
@Test void consolidated_emission_deduplication() { ... }

// 모듈 경계
@Test void modularity_test() { new ModularityTests().verify(); }

// Hash Chain 무결성
@Test void audit_log_hash_chain_detects_tampering() { ... }

// 스냅샷 불변성
@Test void verification_snapshot_cannot_be_modified() { ... }

// YAML 로더 멱등성 (esg-t1 BUG-P5-07 교훈)
@Test void emission_factor_loader_is_item_level_idempotent() { ... }
```

---

## 7-A. 단위 변환 모듈

```java
// UnitConverter.java (도메인 서비스 — 순수 함수)
public class UnitConverter {
    // 기준 단위로의 양방향 변환: from → 기준 단위 → to
    public Quantity convert(Quantity from, String toUnit) {
        // BigDecimal 사용 — double 정밀도 누적 오류 방지
        BigDecimal baseValue = from.value().multiply(factorToBase(from.unit()));
        return new Quantity(baseValue.divide(factorToBase(toUnit), 6, RoundingMode.HALF_UP), toUnit);
    }
}
// 변환 테이블 예시 (unit_conversions 테이블에서 로드)
// GJ → kWh : × 277.778
// TJ → GJ  : × 1000
// Mcal → GJ: × 0.004187
// t → kg   : × 1000
```

---

## 8. 승인 상태 기계 (Approval State Machine)

승인 엔티티는 명시적 상태 전이 메서드만 허용한다. 임의 `status` 문자열 세팅 금지 (esg-t1 교훈).

```java
// ApprovalEntity.java 상태 전이 규칙
public class ApprovalEntity {
    private ApprovalStatus status; // PENDING → APPROVED | REJECTED | ESCALATED

    public void approve(ActorId approver) {
        if (status != PENDING) throw new EsgException(INVALID_STATUS_TRANSITION);
        this.status = APPROVED;
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
    }

    public void reject(ActorId reviewer, String reason) {
        Objects.requireNonNull(reason, "rejection reason required");
        if (reason.isBlank()) throw new EsgException(REJECTION_REASON_REQUIRED);
        this.status = REJECTED;
        this.rejectionReason = reason;
    }

    public void escalate(ActorId escalatedBy) {
        if (status != PENDING) throw new EsgException(INVALID_STATUS_TRANSITION);
        this.status = ESCALATED;
    }
    // setStatus(String) 직접 호출 금지 — 서비스 레이어에서 위 메서드만 사용
}
```

**상태 전이도**:
```
PENDING → APPROVED   (approve())
PENDING → REJECTED   (reject(reason))  ← reason 필수, 공백 불가
PENDING → ESCALATED  (escalate())
ESCALATED → APPROVED (approve())
ESCALATED → REJECTED (reject(reason))
```

---

## 9. 비동기 이벤트 — DB Outbox 패턴

`ApplicationEventPublisher` 직접 사용 시 트랜잭션 롤백 후에도 이벤트가 발행될 수 있다.
신뢰성이 필요한 모듈 간 비동기 통신은 **DB Outbox 패턴** 사용.

```sql
CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    payload     JSONB NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / PROCESSED / FAILED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);
```

```
트랜잭션 커밋
  └─ outbox_events INSERT (동일 트랜잭션)
       └─ OutboxPoller (@Scheduled, 5초 간격)
            └─ ApplicationEventPublisher.publishEvent()
                  └─ @ApplicationModuleListener (수신 모듈)
```

- Outbox Poller: `@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")`
- 중복 방지: `status = PROCESSED` 업데이트 후 재발행 없음
- 실패 시: `status = FAILED` + 에러 로그, 재시도 횟수 제한

---

## 10. 대량 업로드 — Row-level 트랜잭션 (esg-t1 Phase 3 교훈)

CSV 대량 업로드 시 단일 트랜잭션으로 처리하면 하나의 오류가 전체 롤백을 유발한다.
각 행(row)을 독립 트랜잭션으로 처리하여 부분 성공 허용.

```java
@Service
public class CsvBulkImportService {
    private final ActivityDataRowImporter rowImporter; // 별도 빈

    public BulkImportResult importCsv(List<CsvRow> rows) {
        List<ImportRowResult> results = rows.stream()
            .map(row -> rowImporter.importRow(row)) // REQUIRES_NEW 트랜잭션
            .toList();
        return BulkImportResult.from(results);
    }
}

@Service
public class ActivityDataRowImporter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowResult importRow(CsvRow row) {
        try {
            // 단일 행 처리
            return ImportRowResult.success(row.lineNumber());
        } catch (Exception e) {
            return ImportRowResult.failure(row.lineNumber(), e.getMessage());
        }
    }
}
```

---

## 11. N+1 방지 가이드라인

JPA 연관 관계 로딩 정책:
- 기본값: `LAZY` — 모든 @ManyToOne, @OneToMany에 `fetch = FetchType.LAZY`
- 조회 서비스: JPQL `JOIN FETCH` 또는 `@EntityGraph`로 명시적 fetch
- N+1이 의심될 때: `@QueryHints({ @QueryHint(name = "hibernate.query.fetchSize", value = "50") })`
- 통합 테스트에서 Hibernate statistics 활성화로 쿼리 수 검증:

```java
// 테스트에서 N+1 검증
assertThat(statistics.getEntityLoadCount()).isLessThanOrEqualTo(expectedCount);
```

**중요**: 입력 단위와 표준 단위가 다를 경우 항상 **원래 단위를 `activity_data.unit` 컬럼에 보존**하고, 변환된 표준 단위 값을 별도 컬럼에 저장한다. 단위 변환 이력이 감사 가능해야 한다.

---

## 8. 배출계수 로더 설계 (YAML Loader Idempotency)

esg-t1의 BUG-P5-07(파일 레벨 스킵으로 인한 누락) 교훈 적용:

```java
@Transactional
public void load(List<EmissionFactorYaml> factors) {
    for (EmissionFactorYaml yaml : factors) {
        // 항목별 멱등 upsert — 파일 레벨 스킵 금지
        repository.upsert(yaml.toEntity()); // ON CONFLICT DO UPDATE
    }
}
```

**SQL upsert 예시**:
```sql
INSERT INTO emission_factors (source, category, sub_category, country_code, year, factor_value, unit)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT (source, category, sub_category, country_code, year)
DO UPDATE SET factor_value = EXCLUDED.factor_value,
             unit = EXCLUDED.unit,
             is_active = TRUE;
```

---

## 9. 비동기 처리 (DB Outbox Pattern)

```
Service → DB (outbox_events 테이블 저장) → Outbox Poller
→ ApplicationEventPublisher → @ApplicationModuleListener
→ 실제 처리 (AuditLog 기록, 알림 발송 등)
```

```sql
CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(100) NOT NULL,
    payload     JSONB NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);
```

---

## 10. 프론트엔드 구조 개요

```
src/
├── app/                          # Next.js App Router
│   ├── (auth)/login/            # 인증
│   ├── dashboard/               # 메인 대시보드
│   ├── entities/                # 법인 관리
│   ├── data-input/              # 활동 데이터 입력
│   ├── ghg/                     # GHG 계산 결과
│   │   ├── scope1/
│   │   ├── scope2/
│   │   └── scope3/              # 카테고리별 Scope 3
│   ├── reports/                 # 보고서 생성·조회
│   ├── verification/            # 검증 워크스페이스 (VERIFIER UI)
│   ├── supplier/                # 공급업체 포털
│   └── audit/                   # AuditLog 조회
├── components/
│   ├── ui/                      # shadcn/ui 기반 공통 컴포넌트
│   ├── charts/                  # ESG 시각화 차트
│   └── forms/                   # 데이터 입력 폼
├── lib/
│   ├── api/                     # API 클라이언트 (타입 안전)
│   └── utils/                   # 공통 유틸
└── types/                       # TypeScript 타입 정의
```

---

## 11. Scope 3 Category 16 사전 설계

GHG Protocol Phase 1 Update(2026-03)에서 신설 예정. 최종 기준 2027년 말 확정 전 데이터 모델만 준비:

```sql
-- scope3_category 컬럼에 16 허용 (기존 1~15 → 1~16)
-- 계산 로직은 2027년 기준 확정 후 구현
ALTER TABLE emission_records
    ADD CONSTRAINT scope3_category_check
    CHECK (scope3_category IS NULL OR scope3_category BETWEEN 1 AND 16);
```

---

## 12. 성능 최적화 전략

### Virtual Threads (Project Loom)
- Scope 3 배치 계산: 100개 공급업체 데이터 병렬 처리
- `Executors.newVirtualThreadPerTaskExecutor()` 사용

### 데이터베이스 인덱스
```sql
-- 자주 조회되는 필터 조합
CREATE INDEX idx_emission_records_lookup
    ON emission_records(tenant_id, entity_id, reporting_year, scope);

CREATE INDEX idx_activity_data_status
    ON activity_data(tenant_id, entity_id, status, reporting_year);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs(tenant_id, entity_type, entity_id);
```

### 캐시 전략
- Redis: JWT 블랙리스트, 배출계수 마스터 데이터 (TTL: 24시간)
- Spring Cache: 연결 집계 결과 (TTL: 1시간, 데이터 변경 시 무효화)

---

## 13. 검토 이력

| 날짜 | 검토자 | 버전 | 주요 변경 |
|---|---|---|---|
| 2026-05-18 | Claude Code (esg-t2 기획) | 1.0 | 초안 작성 |
