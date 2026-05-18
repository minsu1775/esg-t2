---
name: emission-calculation
description: BigDecimal 전용 산출, 배출계수 과거 재현, 연결 집계, Scope 3 커버리지
paths:
  - "src/main/java/**/ghg/**"
  - "src/main/java/**/ce/**"
  - "**/*Calculator*.java"
  - "**/*Emission*.java"
  - "**/*Converter*.java"
---

# 배출량 계산 규칙

## BigDecimal 전용 산출 정책 (esg-t1 Phase 5 교훈)

배출량·에너지·금액 등 모든 수치 계산에 `float` / `double` **절대 금지**.

```java
// ❌ 금지 — IEEE 754 부동소수점 오차 누적
double result = activity * factor;

// ✅ 필수
BigDecimal result = activity.multiply(factor).setScale(6, RoundingMode.HALF_UP);
```

- DB 컬럼 타입: `DECIMAL(20, 6)` 통일.
- `BigDecimal.valueOf(double)` 금지 → `new BigDecimal("1.23")` 또는 `BigDecimal.valueOf(longVal, scale)`.
- 중간 계산에서도 `double` 캐스팅 금지 (연쇄 정밀도 손실).

## 배출계수 과거 재현 (esg-t1 L-0-09)

배출계수 갱신 시 과거 공시 수치가 변경되면 재현성 위반.
`EmissionFactorResolver.resolveAt(category, date)`로 산출 시점의 계수를 조회해야 함.

```java
// 산출 시점의 계수 조회 (effective_from/to 기간 기반)
EmissionFactor factor = resolver.resolveAt(
    EmissionCategory.SCOPE1_FUEL,
    activityData.reportingDate()
);
// 과거 공시는 당시 계수로 항상 동일하게 재현 가능
```

`emission_factors` 테이블에 `effective_from`, `effective_to` 컬럼 필수.

## 단위 변환 — BigDecimal + UnitConverter 단일 경유

직접 계산 금지. `UnitConverter.convert()` 단일 메서드만 사용.

```java
// ❌ 금지
BigDecimal kwh = gj.multiply(new BigDecimal("277.778"));

// ✅ 필수
Quantity kwh = unitConverter.convert(new Quantity(gj, "GJ"), "kWh");
```

변환 중 `BigDecimal`만 사용. 중간에 `double` 캐스팅 없음.

## 연결 집계 — Equity Method

```
ConsolidatedEmission = Σ (EntityEmission × OwnershipRatio)
```

- 이중 계상 제거 필수: A→B→C 체인에서 중간 법인 배출 중복 방지.
- 순환 지분 구조 탐지 → 예외 발생 (`CircularOwnershipException`).
- `EntityRelationshipGraph`에서 DAG 유효성 검증 후 집계.

## Scope 3 95% 임계값 (GHG Protocol Phase 1 Update 2026-03)

```
포함 카테고리 배출 합계 ÷ 총 추정 배출 ≥ 95%
```

- `Scope3CoverageCalculator`가 포함/제외 카테고리 분류 + 비율 계산.
- Category 16 (Facilitated Emissions): DB 스키마 `scope3_category CHECK 1~16` 허용, 계산 로직은 미구현 (2027년 예정).
- 제외 카테고리는 반드시 `exclusion_reasons` JSONB에 사유 기록.

## 데이터 품질 점수

| 등급 | 설명 |
|---|---|
| `SPEND_BASED` | 지출 기반 (가장 낮은 정확도) |
| `AVERAGE_DATA` | 업계 평균 데이터 |
| `SUPPLIER_SPECIFIC` | 공급업체 실측 데이터 (가장 높은 정확도) |

활동 데이터 저장 시 `data_quality` 필드 필수 설정.

## GWP 기준

IPCC AR6 GWP 기준 사용. 산식 YAML에 `gwp: AR6` 명시.
GWP 기준 변경 시 `emission_factors`에 새 버전으로 INSERT (기존 데이터 불변).

## 원 단위 + 변환 단위 병행 저장

활동 데이터는 입력된 원 단위와 기준 단위 변환값을 모두 저장.

```sql
-- activity_data 컬럼
quantity        NUMERIC(20,6) NOT NULL,   -- 원본 값
unit            VARCHAR(30) NOT NULL,     -- 원본 단위
standard_value  NUMERIC(20,6),           -- 기준 단위 변환값
standard_unit   VARCHAR(30)              -- 기준 단위 (GJ, tCO2e 등)
```
