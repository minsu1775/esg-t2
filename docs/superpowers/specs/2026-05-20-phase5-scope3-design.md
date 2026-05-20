# Phase 5 Scope 3 계산 엔진 — 설계 문서

> **작성일**: 2026-05-20
> **레퍼런스**: task.md T-5-01~T-5-12, spec.md, regulatory.md, plan.md Phase 5
> **상태**: 승인됨

---

## 1. 배경 & 목표

GHG Protocol Scope 3 표준(2026-03 Phase 1 Update 반영)에 따라 Category 1(구매재화·서비스), Category 2(자본재), Category 11(판매제품 사용) 배출량 계산 엔진과 95% 커버리지 보고서 생성 기능을 구현한다.

**주요 규제 맥락**:
- KSSB 2 기준 Scope 3 의무 공시는 2031년부터 (3년 유예). 단, 조기 공시 수요 존재.
- GHG Protocol: 기업은 전체 Scope 3의 95% 이상을 포함한 카테고리를 수량화해야 하며, 제외 카테고리는 사유를 공개해야 함.
- Category 16(Facilitated Emissions): 2027년 최종 기준 예정. 본 Phase에서는 DB 스키마만 준비, 계산 미구현.

---

## 2. 설계 결정 사항

### 2.1 Cat.11 다년도 배출 귀속 공식

#### 검토한 선택지

**옵션 A — 단순 연간 배출 방식**
- 공식: `판매량 × 연간_사용_배출계수 (tCO2e/unit/year)`
- 배출계수 YAML에 "연간" 계수를 직접 정의. `lifetime_years` 개념 없음.
- 장점: 구현 단순, 배출계수에 사용기간이 내재됨
- 단점: 배출계수 YAML 정의 시 "연간"임을 명시해야 하며, 제품별 사용기간 변경 시 계수를 통째로 교체해야 함

**옵션 B — 생애주기 + 사용기간 방식 (선택)**
- 공식: `(판매량 × 생애주기_배출계수) / 사용기간`
- `activity_data`에 `lifetime_years` 필드 추가. 계수는 제품 생애주기 전체 기준.
- 장점: task.md "판매량 × 계수 × 사용기간" 표현과 일치. 사용기간을 독립 변수로 관리하므로 사용기간 변경 시 재산출 가능. GHG Protocol 권고 방식.
- 단점: `activity_data` 스키마 변경 필요, Cat.11 전용 필드

**추천: 옵션 B** — GHG Protocol 권고 방식이며, 사용기간을 독립 관리하면 제품 생애주기 가정 변경 시 감사 추적이 용이함.

**구현 공식**:
```java
// Cat.11: 연간 귀속 배출량
BigDecimal annualEmission = quantity                    // 당해연도 판매량 (units)
    .multiply(factorValue)                             // × 생애주기 배출계수 (tCO2e/unit)
    .divide(lifetimeYears, 6, RoundingMode.HALF_UP);  // ÷ 사용기간 (years)
```

---

### 2.2 Scope 3 커버리지 95% 계산 방식

#### 검토한 선택지

**옵션 A — 배출량 기반 (선택)**
- 공식: `포함된 카테고리 배출 합계 ÷ (포함 배출 + 추정 제외 배출)`
- 사용자가 제외 카테고리의 추정 배출량을 API 요청 시 직접 입력 (`estimatedExcludedEmissions: {4: 1000.0, 6: 500.0}`)
- 장점: GHG Protocol 원칙에 가장 충실. 실제 중요도(배출 규모)가 커버리지에 반영됨.
- 단점: 추정치 입력이 필요. 추정치의 정확성은 사용자 책임.

**옵션 B — 카테고리 수 기반**
- 공식: `포함된 카테고리 수 ÷ 전체 관련 카테고리 수`
- 추정치 입력 불필요. 단순.
- 단점: 배출 규모가 큰 카테고리와 작은 카테고리를 동등하게 취급. GHG Protocol 엄밀 해석과 다름.

**추천: 옵션 A** — 규제 기관(KSSB 2, GHG Protocol)이 배출량 기반 95% 기준을 요구하므로, MVP에서도 올바른 방식으로 구현해야 향후 외부 검증 통과 가능성이 높아짐.

---

### 2.3 Scope 3 API 구조

#### 검토한 선택지

**옵션 A — 기존 GhgService 확장**
- Cat.1/2/11 계산을 `GhgService`에 메서드 추가
- 엔드포인트는 기존 `/calculations`에 `?scope=SCOPE3_CAT1` 파라미터로 구분
- 기존 `EmissionRecord` / `emission_records` 테이블 재사용
- 장점: 파일 수 최소화
- 단점: GhgService가 7~8개 메서드로 비대화. 커버리지 리포트(`scope3_coverage_reports`)는 별개 aggregate로 억지로 끼워야 함.

**옵션 B — Scope3 전용 서비스 + 기존 Controller 통합 (선택)**
- `Scope3Service` 인터페이스(`ghg/api/`) + `DefaultScope3Service` 구현체(`ghg/internal/`) 신규 생성
- 엔드포인트는 기존 `GhgController`에 추가 (Controller 분리하지 않음)
- 장점: 계산 로직 분리(SRP). Phase 4의 `ConsolidationService` 분리 선례와 일관성. 커버리지 리포트가 자연스럽게 Scope3Service 안에 응집.
- 단점: 인터페이스 1개 + 구현 클래스 1개 추가

**추천: 옵션 B** — 이미 Phase 4에서 `ConsolidationService`를 `GhgService`와 분리한 선례가 있음. Scope 3는 데이터 품질 점수, 생애주기 귀속, 커버리지 리포트 등 질적으로 다른 로직이므로 분리가 타당함. Controller는 분리하지 않아 OpenAPI 태그 응집도를 유지함.

---

## 3. 아키텍처 & 컴포넌트 설계

### 3.1 도메인 계층 (`ghg/domain/`)

```
ghg/domain/
├── Scope3Cat1Calculator.java       # 지출 기반 계산 (spend × factor)
├── Scope3Cat2Calculator.java       # 자본재 취득액 계산 (acquisition × factor)
├── Scope3Cat11Calculator.java      # 판매제품 사용 (quantity × factor / lifetime)
├── Scope3CoverageCalculator.java   # 95% 임계값 판단
└── Scope3CoverageReport.java       # 커버리지 보고서 도메인 record (불변)
```

**계산기 간 관계**: 각 계산기는 `EmissionCalculator.computeEmission()`을 위임 호출함. Scope 3 특수 로직(사용기간 나누기, 데이터 품질 분류)은 각 계산기가 담당.

### 3.2 데이터 품질 점수 자동 부여 규칙

`ActivityData` 저장 시 `dataSource`와 배출계수 출처를 기반으로 자동 결정:

| 조건 | 등급 | 설명 |
|---|---|---|
| `dataSource = SUPPLIER_PORTAL` | `SUPPLIER_SPECIFIC` | 공급업체 실측 데이터 |
| `dataSource = API` | `AVERAGE_DATA` | 외부 API 데이터 |
| 그 외 | `SPEND_BASED` | 지출 기반 추정 (기본값) |

### 3.3 Scope3CoverageCalculator 공식

```java
// 배출량 기반 95% 커버리지
BigDecimal includedTotal = /* 포함 카테고리 emission_records 합산 */;
BigDecimal estimatedExcludedTotal = /* 사용자 입력 추정치 합산 */;
BigDecimal totalEstimated = includedTotal.add(estimatedExcludedTotal);

BigDecimal coveragePct = includedTotal
    .divide(totalEstimated, 4, RoundingMode.HALF_UP)
    .multiply(BigDecimal.valueOf(100))
    .setScale(2, RoundingMode.HALF_UP);

boolean meets95 = coveragePct.compareTo(new BigDecimal("95.00")) >= 0;
```

### 3.4 인프라 계층 변경

**신규 마이그레이션**:

| 파일 | 내용 |
|---|---|
| `V18__scope3_tables.sql` | `scope3_coverage_reports` 테이블 생성 + `activity_data.lifetime_years` 컬럼 추가 + `emission_records.scope3_category CHECK (1~16)` 제약 |
| `db/migration-pg/V18__scope3_rls.sql` | `scope3_coverage_reports` RLS 정책 |

**신규 JPA Entity**: `Scope3CoverageReportJpaEntity` + `Scope3CoverageReportRepository`

### 3.5 서비스 인터페이스 (`ghg/api/`)

```java
public interface Scope3Service {

    // Cat.1: 지출 기반 계산 (SPEND_BASED → 데이터 품질 자동 부여)
    EmissionRecordResponse calculateCat1(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.2: 자본재 취득액 계산
    EmissionRecordResponse calculateCat2(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.11: 판매제품 사용 (생애주기 귀속)
    EmissionRecordResponse calculateCat11(UUID tenantId, UUID entityId, int reportingYear);

    // 커버리지 리포트 생성 (배출량 기반 95% 판단)
    // estimatedExcludedEmissions: key=카테고리 번호(1~16), value=추정 배출량(tCO2e)
    // 예: {4: 1000.0, 6: 500.0} → Cat.4, Cat.6 추정치 포함
    Scope3CoverageResponse generateCoverageReport(
        UUID tenantId, UUID entityId, int reportingYear,
        Map<Integer, BigDecimal> estimatedExcludedEmissions
    );

    // 커버리지 리포트 조회
    Scope3CoverageResponse getCoverageReport(UUID tenantId, UUID entityId, int reportingYear);
}
```

### 3.6 API 엔드포인트 (`GhgController`에 추가)

```
POST /api/v1/ghg/entities/{id}/scope3/cat1/calculations   @Auditable("SCOPE3_CAT1_CALCULATED")
POST /api/v1/ghg/entities/{id}/scope3/cat2/calculations   @Auditable("SCOPE3_CAT2_CALCULATED")
POST /api/v1/ghg/entities/{id}/scope3/cat11/calculations  @Auditable("SCOPE3_CAT11_CALCULATED")
POST /api/v1/ghg/entities/{id}/scope3/coverage-report     @Auditable("SCOPE3_COVERAGE_GENERATED")
GET  /api/v1/ghg/entities/{id}/scope3/coverage-report
```

모든 엔드포인트 `@PreAuthorize("hasRole('ESG_MANAGER')")` (GET은 `ESG_VIEWER`도 허용).

---

## 4. 보안 & 불변성 원칙

- `scope3_coverage_reports`: Append-only (`Repository<T,ID>` 마커 인터페이스)
- `scope3_coverage_reports` RLS: `tenant_isolation` 정책 (기존 패턴과 동일)
- `lifetime_years` 필드: Cat.11 이외 활동 데이터는 `null` 허용 (강제 입력 아님)
- 배출량 기반 커버리지: 제외 카테고리 추정치는 `exclusion_reasons` JSONB에 반드시 기록

---

## 5. 테스트 전략

### 5.1 도메인 단위 테스트

> **단위 주의**: Cat.1/Cat.2의 `quantity`는 지출금액(통화). `unit` 필드에 `KRW` 또는 `USD` 등 통화 코드 저장. 배출계수 단위는 `tCO2e/KRW` 또는 `tCO2e/USD`.

| 테스트 클래스 | 핵심 케이스 |
|---|---|
| `Scope3Cat1CalculatorTest` | 지출 10,000 KRW × 계수 0.0005 tCO2e/KRW = 5.0 tCO2e |
| `Scope3Cat1CalculatorTest` | 데이터 품질: `dataSource=SUPPLIER_PORTAL` → `SUPPLIER_SPECIFIC` |
| `Scope3Cat1CalculatorTest` | 데이터 품질: `dataSource=MANUAL` → `SPEND_BASED` |
| `Scope3Cat2CalculatorTest` | 자본재 취득액 500,000 KRW × 계수 0.0003 tCO2e/KRW = 150.0 tCO2e |
| `Scope3Cat11CalculatorTest` | TV 1,000대 × 0.5 tCO2e/대(생애) ÷ 8년 = 62.5 tCO2e/year |
| `Scope3Cat11CalculatorTest` | `lifetimeYears = 0` → `IllegalArgumentException` |
| `Scope3Cat11CalculatorTest` | `lifetimeYears = null` (Cat.11 아닌 데이터) → `IllegalArgumentException` |
| `Scope3CoverageCalculatorTest` | Cat1=800, Cat2=150, 추정제외=50 → 95.00% → meets threshold ✅ |
| `Scope3CoverageCalculatorTest` | Cat1=700, 추정제외=300 → 70.00% → threshold 미달 ❌ |
| `Scope3CoverageCalculatorTest` | 추정제외 합계 0 (모든 카테고리 포함) → 100.00% |

### 5.2 통합 테스트 (`Scope3IntegrationTest`)

- Cat.1/2/11 계산 → `emission_records` 저장 (append-only), `scope3_category` 컬럼 값 확인
- 커버리지 리포트 생성 → `scope3_coverage_reports` 저장 + `@Auditable` → AuditLog 기록
- `ModularityTest` 통과 (모듈 경계 위반 없음)

### 5.3 보안 테스트

- `ESG_VIEWER` → POST 엔드포인트 → 403 확인
- 크로스 테넌트 커버리지 리포트 조회 → 404 확인

---

## 6. 태스크 매핑

| 태스크 ID | 내용 | 우선순위 |
|---|---|---|
| T-5-01 | `test:` Cat.1 지출 기반 계산 정확도 | 1 |
| T-5-02 | `feat:` Scope3Cat1Calculator | 1 |
| T-5-03 | `feat:` 데이터 품질 점수 자동 부여 | 1 |
| T-5-04 | `test:` Cat.2 자본재 취득액 계산 | 2 |
| T-5-05 | `feat:` Scope3Cat2Calculator | 2 |
| T-5-06 | `test:` Cat.11 다년도 배출 귀속 정확도 | 3 |
| T-5-07 | `feat:` Scope3Cat11Calculator | 3 |
| T-5-08 | `feat:` V18__scope3_tables.sql + RLS | 4 |
| T-5-09 | `test:` 95% 임계값 판단 정확도 | 4 |
| T-5-10 | `feat:` Scope3CoverageCalculator | 4 |
| T-5-11 | `feat:` GET/POST scope3/coverage-report API | 5 |
| T-5-12 | `feat:` Category 16 DB 스키마 준비 (계산 미구현) | 5 |
