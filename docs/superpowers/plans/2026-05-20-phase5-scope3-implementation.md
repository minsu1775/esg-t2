# Phase 5 Scope 3 계산 엔진 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GHG Protocol Scope 3 Cat.1/2/11 배출량 계산 엔진과 95% 커버리지 보고서 생성 API를 TDD로 구현한다.

**Architecture:** `Scope3Cat1/2/11Calculator` 순수 도메인 서비스 → `Scope3CoverageCalculator` → `DefaultScope3Service`(`ghg/internal/`) → `GhgController` 엔드포인트 추가. 커버리지 리포트는 `scope3_coverage_reports` 별도 aggregate (append-only). 기존 `EmissionRecord`에 `scope3Category` 필드 추가, `ActivityData`에 `lifetimeYears` 필드 추가.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate 7, PostgreSQL 18 (Testcontainers), AssertJ, JUnit 5.

---

## 파일 구조

### 신규 파일

| 경로 | 역할 |
|---|---|
| `ghg/domain/Scope3Cat1Calculator.java` | Cat.1 지출 기반 계산 + 데이터 품질 분류 |
| `ghg/domain/Scope3Cat2Calculator.java` | Cat.2 자본재 취득액 계산 |
| `ghg/domain/Scope3Cat11Calculator.java` | Cat.11 생애주기 귀속 계산 |
| `ghg/domain/Scope3CoverageReport.java` | 커버리지 보고서 도메인 record (불변) |
| `ghg/domain/Scope3CoverageCalculator.java` | 배출량 기반 95% 임계값 판단 |
| `ghg/api/Scope3Service.java` | Scope3 공개 서비스 인터페이스 |
| `ghg/api/Scope3CoverageRequest.java` | 커버리지 리포트 생성 요청 DTO |
| `ghg/api/Scope3CoverageResponse.java` | 커버리지 리포트 응답 DTO |
| `ghg/infra/Scope3CoverageReportJpaEntity.java` | JPA Entity (append-only) |
| `ghg/infra/Scope3CoverageReportRepository.java` | Append-only Repository |
| `ghg/internal/DefaultScope3Service.java` | 서비스 구현체 |
| `db/migration/V18__scope3_tables.sql` | scope3_coverage_reports 생성, lifetime_years·scope3_category 컬럼 추가 |
| `db/migration-pg/V18__scope3_rls.sql` | scope3_coverage_reports RLS 정책 |
| `ghg/domain/Scope3CalculatorTest.java` (test) | 도메인 단위 테스트 |
| `ghg/Scope3IntegrationTest.java` (test) | 통합 테스트 |

### 수정 파일

| 경로 | 변경 내용 |
|---|---|
| `ghg/domain/ActivityData.java` | `lifetimeYears: Integer` 필드 추가 |
| `ghg/domain/CreateActivityDataCommand.java` | `lifetimeYears: Integer` 필드 추가 |
| `ghg/domain/EmissionRecord.java` | `scope3Category: Integer` 필드 추가, calculate() 시그니처 변경 |
| `ghg/api/CreateActivityDataRequest.java` | `lifetimeYears: Integer` 필드 추가 (nullable) |
| `ghg/infra/ActivityDataJpaEntity.java` | `lifetimeYears` 컬럼 필드 추가 |
| `ghg/infra/ActivityDataMapper.java` | `lifetimeYears` 매핑 추가 |
| `ghg/infra/EmissionRecordJpaEntity.java` | `scope3Category` 필드 추가 |
| `ghg/infra/EmissionRecordMapper.java` | `scope3Category` 매핑 추가 |
| `ghg/infra/EmissionRecordRepository.java` | Scope3 전용 조회 쿼리 추가 |
| `ghg/internal/DefaultGhgService.java` | `EmissionRecord.calculate()` 호출 시 `scope3Category=null` 추가 |
| `ghg/api/GhgController.java` | Scope3 엔드포인트 5개 추가, `Scope3Service` 주입 |

---

## Task 1: 도메인 단위 테스트 작성 — Cat.1 / Cat.2 계산기 (Red)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java`

- [ ] **Step 1: 테스트 파일 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Scope3CalculatorTest {

    @Nested
    class Cat1 {

        @Test
        void 지출기반_CO2e_계산_정확도() {
            // 10,000 KRW × 0.0005 tCO2e/KRW = 5.000000 tCO2e
            BigDecimal result = Scope3Cat1Calculator.computeEmission(
                new BigDecimal("10000"), new BigDecimal("0.0005"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("5.000000"));
        }

        @Test
        void SUPPLIER_PORTAL_출처는_SUPPLIER_SPECIFIC_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("SUPPLIER_PORTAL"))
                .isEqualTo("SUPPLIER_SPECIFIC");
        }

        @Test
        void API_출처는_AVERAGE_DATA_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("API"))
                .isEqualTo("AVERAGE_DATA");
        }

        @Test
        void MANUAL_출처는_SPEND_BASED_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("MANUAL"))
                .isEqualTo("SPEND_BASED");
        }

        @Test
        void CSV_UPLOAD_출처는_SPEND_BASED_품질() {
            assertThat(Scope3Cat1Calculator.deriveDataQuality("CSV_UPLOAD"))
                .isEqualTo("SPEND_BASED");
        }

        @Test
        void 음수_지출액은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat1Calculator.computeEmission(new BigDecimal("-1"), new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Cat2 {

        @Test
        void 자본재_취득액_CO2e_계산_정확도() {
            // 500,000 KRW × 0.0003 tCO2e/KRW = 150.000000 tCO2e
            BigDecimal result = Scope3Cat2Calculator.computeEmission(
                new BigDecimal("500000"), new BigDecimal("0.0003"));
            assertThat(result).isEqualByComparingTo(new BigDecimal("150.000000"));
        }

        @Test
        void 음수_취득액은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat2Calculator.computeEmission(new BigDecimal("-100"), new BigDecimal("0.3")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 오류 확인 (Red)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest" 2>&1 | head -30
```

Expected: `error: cannot find symbol class Scope3Cat1Calculator`

- [ ] **Step 3: test: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java
git commit -m "test: Scope3 Cat.1/Cat.2 계산기 단위 테스트 (Red)"
```

---

## Task 2: Scope3Cat1Calculator + Scope3Cat2Calculator 구현 (Green)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat1Calculator.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat2Calculator.java`

- [ ] **Step 1: Scope3Cat1Calculator 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public final class Scope3Cat1Calculator {

    private Scope3Cat1Calculator() {}

    // 지출 기반 배출량: spend × factor (tCO2e/통화단위)
    public static BigDecimal computeEmission(BigDecimal spendAmount, BigDecimal factorValue) {
        if (spendAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("지출액은 0 이상이어야 합니다: " + spendAmount);
        }
        return EmissionCalculator.computeEmission(spendAmount, factorValue);
    }

    // dataSource 기반 데이터 품질 자동 결정
    public static String deriveDataQuality(String dataSource) {
        if (dataSource == null) return "SPEND_BASED";
        return switch (dataSource) {
            case "SUPPLIER_PORTAL" -> "SUPPLIER_SPECIFIC";
            case "API"             -> "AVERAGE_DATA";
            default                -> "SPEND_BASED";
        };
    }
}
```

- [ ] **Step 2: Scope3Cat2Calculator 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public final class Scope3Cat2Calculator {

    private Scope3Cat2Calculator() {}

    // 자본재 취득액 기반 배출량: acquisitionCost × factor (tCO2e/통화단위)
    public static BigDecimal computeEmission(BigDecimal acquisitionCost, BigDecimal factorValue) {
        if (acquisitionCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("자본재 취득액은 0 이상이어야 합니다: " + acquisitionCost);
        }
        return EmissionCalculator.computeEmission(acquisitionCost, factorValue);
    }
}
```

- [ ] **Step 3: 테스트 실행 → 통과 확인 (Green)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest.Cat1"
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest.Cat2"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: feat: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat1Calculator.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat2Calculator.java
git commit -m "feat: Scope3 Cat.1/Cat.2 계산기 구현"
```

---

## Task 3: Cat.11 테스트 추가 (Red)

**Files:**
- Modify: `src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java`

- [ ] **Step 1: Scope3CalculatorTest에 Cat11 중첩 클래스 추가**

`Scope3CalculatorTest` 클래스 내부 `Cat2` 블록 아래에 추가:

```java
    @Nested
    class Cat11 {

        @Test
        void 생애주기_배출_연간_귀속_계산_정확도() {
            // TV 1,000대 × 0.5 tCO2e/대 ÷ 8년 = 62.500000 tCO2e/year
            BigDecimal result = Scope3Cat11Calculator.computeAnnualEmission(
                new BigDecimal("1000"), new BigDecimal("0.5"), 8);
            assertThat(result).isEqualByComparingTo(new BigDecimal("62.500000"));
        }

        @Test
        void 사용기간이_1년이면_전체_생애주기_배출_그대로() {
            BigDecimal result = Scope3Cat11Calculator.computeAnnualEmission(
                new BigDecimal("100"), new BigDecimal("2.0"), 1);
            assertThat(result).isEqualByComparingTo(new BigDecimal("200.000000"));
        }

        @Test
        void 사용기간_0이면_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("1000"), new BigDecimal("0.5"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용기간");
        }

        @Test
        void 사용기간_null이면_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("1000"), new BigDecimal("0.5"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용기간");
        }

        @Test
        void 음수_판매량은_예외() {
            assertThatThrownBy(() ->
                Scope3Cat11Calculator.computeAnnualEmission(
                    new BigDecimal("-10"), new BigDecimal("0.5"), 5))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
```

- [ ] **Step 2: 테스트 실행 → 컴파일 오류 확인 (Red)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest.Cat11" 2>&1 | head -20
```

Expected: `error: cannot find symbol class Scope3Cat11Calculator`

- [ ] **Step 3: test: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java
git commit -m "test: Scope3 Cat.11 생애주기 귀속 계산기 단위 테스트 (Red)"
```

---

## Task 4: Scope3Cat11Calculator 구현 + ActivityData 확장 (Green)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat11Calculator.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/domain/ActivityData.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/domain/CreateActivityDataCommand.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/api/CreateActivityDataRequest.java`

- [ ] **Step 1: Scope3Cat11Calculator 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Scope3Cat11Calculator {

    private Scope3Cat11Calculator() {}

    // 연간 귀속 배출량 = (판매량 × 생애주기 배출계수) ÷ 사용기간
    // GHG Protocol: 판매연도에 전체 생애 배출을 한 번에 계상하지 않고 사용기간으로 분할
    public static BigDecimal computeAnnualEmission(
            BigDecimal quantity, BigDecimal factorValue, Integer lifetimeYears) {
        if (lifetimeYears == null || lifetimeYears <= 0) {
            throw new IllegalArgumentException("사용기간(lifetimeYears)은 1 이상이어야 합니다: " + lifetimeYears);
        }
        BigDecimal lifetimeEmission = EmissionCalculator.computeEmission(quantity, factorValue);
        return lifetimeEmission.divide(BigDecimal.valueOf(lifetimeYears), 6, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 2: CreateActivityDataCommand에 lifetimeYears 추가**

기존 파일 전체를 아래로 교체:

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateActivityDataCommand(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality,
    Integer lifetimeYears   // Cat.11 전용 (nullable)
) {
    public CreateActivityDataCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId 필수");
        if (entityId == null) throw new IllegalArgumentException("entityId 필수");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category 필수");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("활동량은 0 이상이어야 합니다");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit 필수");
        if (countryCode == null || countryCode.isBlank()) throw new IllegalArgumentException("countryCode 필수");
    }
}
```

- [ ] **Step 3: ActivityData에 lifetimeYears 추가**

기존 파일 전체를 아래로 교체:

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record ActivityData(
    UUID id,
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality,
    BigDecimal standardValue,
    String standardUnit,
    Integer lifetimeYears    // Cat.11 전용 (nullable)
) {
    public static ActivityData create(CreateActivityDataCommand cmd) {
        String stdUnit = UnitConverter.standardUnitFor(cmd.unit());
        BigDecimal stdValue = null;
        if (stdUnit != null && !stdUnit.equals(cmd.unit())) {
            try {
                stdValue = UnitConverter.convert(cmd.quantity(), cmd.unit(), stdUnit);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (stdUnit != null) {
            stdValue = cmd.quantity();
        }
        return new ActivityData(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : "MANUAL",
            cmd.dataQuality() != null ? cmd.dataQuality() : "AVERAGE_DATA",
            stdValue, stdUnit,
            cmd.lifetimeYears()
        );
    }
}
```

- [ ] **Step 4: CreateActivityDataRequest에 lifetimeYears 추가**

```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "활동 데이터 등록 요청")
public record CreateActivityDataRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull @Min(2020) int reportingYear,

    @Schema(description = "GHG 카테고리", example = "SCOPE3_CAT1")
    @NotBlank String category,

    @Schema(description = "세부 카테고리 (선택)", example = "ELECTRONICS")
    String subCategory,

    @Schema(description = "활동량 (Cat.1/2: 지출금액, Cat.11: 판매량)", example = "10000.0")
    @NotNull @PositiveOrZero BigDecimal quantity,

    @Schema(description = "단위 (Cat.1/2: 통화코드, Cat.11: units)", example = "KRW")
    @NotBlank String unit,

    @Schema(description = "국가 코드 (ISO 3166-1)", example = "KR")
    @NotBlank String countryCode,

    @Schema(description = "데이터 출처", example = "MANUAL")
    String dataSource,

    @Schema(description = "데이터 품질 (Cat.1은 자동 결정)", example = "AVERAGE_DATA")
    String dataQuality,

    @Schema(description = "제품 사용기간 (Cat.11 전용, 단위: 년)", example = "8")
    Integer lifetimeYears
) {}
```

- [ ] **Step 5: DefaultGhgService.createActivityData() 수정 — lifetimeYears 전달**

`DefaultGhgService.createActivityData()` 내 `CreateActivityDataCommand` 생성 부분을 수정:

```java
var cmd = new CreateActivityDataCommand(
    tenantId, entityId,
    request.reportingYear(), request.category(), request.subCategory(),
    request.quantity(), request.unit(), request.countryCode(),
    request.dataSource(), request.dataQuality(),
    request.lifetimeYears());   // 추가
```

- [ ] **Step 6: 테스트 실행 → 통과 확인 (Green)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest.Cat11"
./gradlew test --tests "ai.claudecode.esgt2.ghg.*"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: feat: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3Cat11Calculator.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/ActivityData.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/CreateActivityDataCommand.java \
        src/main/java/ai/claudecode/esgt2/ghg/api/CreateActivityDataRequest.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultGhgService.java
git commit -m "feat: Scope3 Cat.11 계산기 + ActivityData lifetimeYears 필드 추가"
```

---

## Task 5: Scope3CoverageCalculator 테스트 추가 (Red)

**Files:**
- Modify: `src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java`

- [ ] **Step 1: Scope3CalculatorTest에 CoverageCalculator 중첩 클래스 추가**

`Cat11` 블록 아래에 추가:

```java
    @Nested
    class CoverageCalculator {

        private final java.util.UUID TENANT_ID = java.util.UUID.randomUUID();
        private final java.util.UUID ENTITY_ID = java.util.UUID.randomUUID();

        @Test
        void 포함_배출_비율_95_이상이면_threshold_충족() {
            // Cat1=800, Cat2=150 → includedTotal=950, estimatedExcluded=50 → 95.00%
            var included = java.util.Map.of(1, new BigDecimal("800"), 2, new BigDecimal("150"));
            var excluded  = java.util.Map.of(4, new BigDecimal("50"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                included, excluded, java.util.Map.of(4, "중요성 낮음"));

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("95.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
            assertThat(report.includedCategories()).containsExactlyInAnyOrder(1, 2);
            assertThat(report.excludedCategories()).containsExactlyInAnyOrder(4);
        }

        @Test
        void 포함_배출_비율_95_미만이면_threshold_미달() {
            // Cat1=700, estimatedExcluded Cat4=300 → 70.00%
            var included = java.util.Map.of(1, new BigDecimal("700"));
            var excluded  = java.util.Map.of(4, new BigDecimal("300"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025, included, excluded, java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("70.00"));
            assertThat(report.meets95PctThreshold()).isFalse();
        }

        @Test
        void 제외_추정치_없으면_100퍼센트_달성() {
            var included = java.util.Map.of(1, new BigDecimal("500"), 2, new BigDecimal("200"));

            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                included, java.util.Map.of(), java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
        }

        @Test
        void 포함과_제외_모두_비어있으면_100퍼센트() {
            Scope3CoverageReport report = Scope3CoverageCalculator.calculate(
                TENANT_ID, ENTITY_ID, 2025,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of());

            assertThat(report.coveragePct()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(report.meets95PctThreshold()).isTrue();
        }
    }
```

- [ ] **Step 2: 테스트 실행 → 컴파일 오류 확인 (Red)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest.CoverageCalculator" 2>&1 | head -20
```

Expected: `error: cannot find symbol class Scope3CoverageReport`

- [ ] **Step 3: test: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/domain/Scope3CalculatorTest.java
git commit -m "test: Scope3CoverageCalculator 95% 임계값 판단 단위 테스트 (Red)"
```

---

## Task 6: Scope3CoverageReport + Scope3CoverageCalculator 구현 (Green)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3CoverageReport.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3CoverageCalculator.java`

- [ ] **Step 1: Scope3CoverageReport 도메인 record 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Scope3CoverageReport(
    UUID id,
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    List<Integer> includedCategories,     // 실제 배출량이 있는 카테고리 번호
    List<Integer> excludedCategories,     // 추정치만 있는 카테고리 번호
    Map<Integer, String> exclusionReasons, // 제외 사유 (카테고리번호 → 사유)
    BigDecimal coveragePct,               // 배출량 기반 커버리지 비율 (0~100)
    boolean meets95PctThreshold           // 95% 임계값 충족 여부
) {}
```

- [ ] **Step 2: Scope3CoverageCalculator 생성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Scope3CoverageCalculator {

    private static final BigDecimal THRESHOLD = new BigDecimal("95.00");

    private Scope3CoverageCalculator() {}

    /**
     * 배출량 기반 95% 커버리지 계산 (GHG Protocol Phase 1 Update 2026-03).
     *
     * @param includedEmissions       실제 배출량 보유 카테고리 (카테고리번호 → tCO2e)
     * @param estimatedExcludedEmissions 제외 카테고리 추정 배출량 (카테고리번호 → tCO2e)
     * @param exclusionReasons        제외 사유 (카테고리번호 → 사유 텍스트)
     */
    public static Scope3CoverageReport calculate(
            UUID tenantId, UUID entityId, int reportingYear,
            Map<Integer, BigDecimal> includedEmissions,
            Map<Integer, BigDecimal> estimatedExcludedEmissions,
            Map<Integer, String> exclusionReasons) {

        BigDecimal includedTotal = includedEmissions.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal excludedTotal = estimatedExcludedEmissions.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEstimated = includedTotal.add(excludedTotal);

        BigDecimal coveragePct;
        if (totalEstimated.compareTo(BigDecimal.ZERO) == 0) {
            coveragePct = new BigDecimal("100.00");
        } else {
            coveragePct = includedTotal
                .divide(totalEstimated, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        }

        return new Scope3CoverageReport(
            UUID.randomUUID(),
            tenantId, entityId, reportingYear,
            new ArrayList<>(includedEmissions.keySet()),
            new ArrayList<>(estimatedExcludedEmissions.keySet()),
            exclusionReasons,
            coveragePct,
            coveragePct.compareTo(THRESHOLD) >= 0
        );
    }
}
```

- [ ] **Step 3: 전체 도메인 단위 테스트 실행 → 통과 확인 (Green)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.Scope3CalculatorTest"
```

Expected: `BUILD SUCCESSFUL` (12건 이상 통과)

- [ ] **Step 4: feat: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3CoverageReport.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/Scope3CoverageCalculator.java
git commit -m "feat: Scope3CoverageReport + Scope3CoverageCalculator 95% 임계값 판단 구현"
```

---

## Task 7: DB 마이그레이션 V18 + EmissionRecord / ActivityData 인프라 확장

**Files:**
- Create: `src/main/resources/db/migration/V18__scope3_tables.sql`
- Create: `src/main/resources/db/migration-pg/V18__scope3_rls.sql`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/domain/EmissionRecord.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordJpaEntity.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordMapper.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataJpaEntity.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataMapper.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordRepository.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultGhgService.java`

- [ ] **Step 1: V18__scope3_tables.sql 생성**

```sql
-- activity_data: Cat.11 사용기간 컬럼 추가 (nullable, Cat.11 전용)
ALTER TABLE activity_data ADD COLUMN IF NOT EXISTS lifetime_years INT;

-- emission_records: Scope 3 카테고리 번호 컬럼 추가 (1~16, Category 16 스키마 준비)
ALTER TABLE emission_records ADD COLUMN IF NOT EXISTS scope3_category INT;
ALTER TABLE emission_records
    ADD CONSTRAINT emission_records_scope3_category_check
    CHECK (scope3_category IS NULL OR (scope3_category >= 1 AND scope3_category <= 16));

-- scope3_coverage_reports: 커버리지 보고서 (append-only, P1 재현성)
CREATE TABLE scope3_coverage_reports (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    entity_id             UUID NOT NULL REFERENCES legal_entities(id),
    reporting_year        INT NOT NULL,
    included_categories   TEXT NOT NULL,   -- JSON 배열: "[1,2,11]"
    excluded_categories   TEXT,            -- JSON 배열: "[4,6]"
    exclusion_reasons     TEXT,            -- JSON 객체: {"4":"사유","6":"사유"}
    coverage_pct          NUMERIC(5, 2) NOT NULL,
    meets_95pct_threshold BOOLEAN NOT NULL,
    generated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scope3_coverage_tenant_entity
    ON scope3_coverage_reports(tenant_id, entity_id, reporting_year);
```

- [ ] **Step 2: db/migration-pg/V18__scope3_rls.sql 생성**

```sql
-- scope3_coverage_reports RLS (P0: 테넌트 격리)
ALTER TABLE scope3_coverage_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON scope3_coverage_reports
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Append-only 강제: DELETE/UPDATE 권한 박탈 (08-persistence.md)
REVOKE UPDATE, DELETE ON scope3_coverage_reports FROM app_user;
```

- [ ] **Step 3: EmissionRecord 도메인 record에 scope3Category 추가**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record EmissionRecord(
    UUID id,
    UUID tenantId,
    UUID entityId,
    UUID activityDataId,
    int reportingYear,
    String scope,
    Integer scope3Category,   // Scope 3 카테고리 번호 (1~16), Scope 1/2는 null
    String ghgType,
    UUID emissionFactorId,
    BigDecimal rawEmission
) {
    public static EmissionRecord calculate(
            UUID tenantId, UUID entityId, UUID activityDataId,
            int reportingYear, String scope, Integer scope3Category,
            String ghgType, UUID emissionFactorId, BigDecimal rawEmission) {
        if (rawEmission == null || rawEmission.signum() < 0) {
            throw new IllegalArgumentException("rawEmission must be non-negative");
        }
        return new EmissionRecord(
            UUID.randomUUID(), tenantId, entityId, activityDataId,
            reportingYear, scope, scope3Category, ghgType, emissionFactorId, rawEmission
        );
    }
}
```

- [ ] **Step 4: EmissionRecordJpaEntity에 scope3Category 필드 추가**

기존 파일에서 `private boolean isConsolidated;` 위에 아래 필드 추가:

```java
    private Integer scope3Category;   // Scope 3 카테고리 번호 (1~16), Scope 1/2는 null
```

그리고 `@Builder` 생성자 파라미터와 초기화 라인 추가:

```java
    @Builder
    public EmissionRecordJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                    UUID activityDataId, int reportingYear,
                                    String scope, Integer scope3Category,   // 추가
                                    String ghgType, UUID emissionFactorId, BigDecimal rawEmission) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.activityDataId = activityDataId;
        this.reportingYear = reportingYear;
        this.scope = scope;
        this.scope3Category = scope3Category;   // 추가
        this.ghgType = ghgType != null ? ghgType : "CO2E";
        this.emissionFactorId = emissionFactorId;
        this.rawEmission = rawEmission;
        this.isConsolidated = false;
        this.calculatedAt = Instant.now();
    }
```

- [ ] **Step 5: EmissionRecordMapper에 scope3Category 매핑 추가**

```java
package ai.claudecode.esgt2.ghg.infra;

import ai.claudecode.esgt2.ghg.domain.EmissionRecord;

public final class EmissionRecordMapper {

    private EmissionRecordMapper() {}

    public static EmissionRecordJpaEntity toEntity(EmissionRecord domain) {
        return EmissionRecordJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .activityDataId(domain.activityDataId())
            .reportingYear(domain.reportingYear())
            .scope(domain.scope())
            .scope3Category(domain.scope3Category())   // 추가
            .ghgType(domain.ghgType())
            .emissionFactorId(domain.emissionFactorId())
            .rawEmission(domain.rawEmission())
            .build();
    }
}
```

- [ ] **Step 6: ActivityDataJpaEntity에 lifetimeYears 필드 추가**

기존 파일에서 `private UUID submittedBy;` 위에 아래 필드 추가:

```java
    private Integer lifetimeYears;   // Cat.11 전용 (nullable)
```

그리고 `@Builder` 생성자 파라미터와 초기화 추가:

```java
    @Builder
    public ActivityDataJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                  int reportingYear, String category, String subCategory,
                                  BigDecimal quantity, String unit, String countryCode,
                                  BigDecimal standardValue, String standardUnit,
                                  String dataSource, String dataQuality,
                                  Integer lifetimeYears) {   // 추가
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.category = category;
        this.subCategory = subCategory;
        this.quantity = quantity;
        this.unit = unit;
        this.countryCode = countryCode;
        this.standardValue = standardValue;
        this.standardUnit = standardUnit;
        this.dataSource = dataSource != null ? dataSource : "MANUAL";
        this.dataQuality = dataQuality != null ? dataQuality : "AVERAGE_DATA";
        this.lifetimeYears = lifetimeYears;   // 추가
        this.status = "DRAFT";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
```

- [ ] **Step 7: ActivityDataMapper에 lifetimeYears 매핑 추가**

```java
package ai.claudecode.esgt2.ghg.infra;

import ai.claudecode.esgt2.ghg.domain.ActivityData;

public final class ActivityDataMapper {

    private ActivityDataMapper() {}

    public static ActivityDataJpaEntity toEntity(ActivityData domain) {
        return ActivityDataJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .reportingYear(domain.reportingYear())
            .category(domain.category())
            .subCategory(domain.subCategory())
            .quantity(domain.quantity())
            .unit(domain.unit())
            .countryCode(domain.countryCode())
            .dataSource(domain.dataSource())
            .dataQuality(domain.dataQuality())
            .standardValue(domain.standardValue())
            .standardUnit(domain.standardUnit())
            .lifetimeYears(domain.lifetimeYears())   // 추가
            .build();
    }
}
```

- [ ] **Step 8: DefaultGhgService.calculateEmissions()에 scope3Category=null 추가**

`DefaultGhgService` 내 `EmissionRecord.calculate()` 호출 부분을 수정:

```java
var domain = EmissionRecord.calculate(
    ad.getTenantId(), ad.getEntityId(), ad.getId(),
    ad.getReportingYear(), scope, null,  // scope3Category: Scope1/2는 null
    "CO2E", factor.id(), emission);
```

- [ ] **Step 9: EmissionRecordRepository에 Scope3 전용 조회 쿼리 추가**

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 커버리지 리포트용: 해당 법인/연도의 Scope3 배출 기록 전체 조회
@Query("SELECT e FROM EmissionRecordJpaEntity e " +
       "WHERE e.tenantId = :tenantId AND e.entityId = :entityId " +
       "AND e.reportingYear = :reportingYear AND e.scope = 'SCOPE3'")
List<EmissionRecordJpaEntity> findScope3ByTenantIdAndEntityIdAndReportingYear(
    @Param("tenantId") UUID tenantId,
    @Param("entityId") UUID entityId,
    @Param("reportingYear") int reportingYear);
```

- [ ] **Step 10: 전체 테스트 실행 → 통과 확인**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` (기존 테스트 모두 통과)

- [ ] **Step 11: feat: 커밋**

```bash
git add src/main/resources/db/migration/V18__scope3_tables.sql \
        src/main/resources/db/migration-pg/V18__scope3_rls.sql \
        src/main/java/ai/claudecode/esgt2/ghg/domain/EmissionRecord.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordJpaEntity.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordMapper.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataJpaEntity.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataMapper.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/EmissionRecordRepository.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultGhgService.java
git commit -m "feat: V18 DB 마이그레이션 + EmissionRecord scope3Category + ActivityData lifetimeYears 인프라 확장"
```

---

## Task 8: Scope3Service 인터페이스 + JPA 인프라 + DefaultScope3Service 구현

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/Scope3Service.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/Scope3CoverageRequest.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/Scope3CoverageResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/infra/Scope3CoverageReportJpaEntity.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/infra/Scope3CoverageReportRepository.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultScope3Service.java`

- [ ] **Step 1: Scope3Service 인터페이스 생성**

```java
package ai.claudecode.esgt2.ghg.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Scope3Service {

    // Cat.1: 지출 기반 배출량 산출 (entity의 해당 연도 SCOPE3_CAT1 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat1(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.2: 자본재 배출량 산출 (entity의 해당 연도 SCOPE3_CAT2 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat2(UUID tenantId, UUID entityId, int reportingYear);

    // Cat.11: 판매제품 사용 배출량 산출 (entity의 해당 연도 SCOPE3_CAT11 활동 데이터 전체)
    List<EmissionRecordResponse> calculateCat11(UUID tenantId, UUID entityId, int reportingYear);

    // 커버리지 보고서 생성 (배출량 기반 95% 임계값 판단)
    Scope3CoverageResponse generateCoverageReport(UUID tenantId, UUID entityId,
        int reportingYear, Scope3CoverageRequest request);

    // 최신 커버리지 보고서 조회
    Scope3CoverageResponse getCoverageReport(UUID tenantId, UUID entityId, int reportingYear);
}
```

- [ ] **Step 2: Scope3CoverageRequest DTO 생성**

```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Scope 3 커버리지 보고서 생성 요청")
public record Scope3CoverageRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull int reportingYear,

    @Schema(description = "제외 카테고리 추정 배출량 (카테고리번호 → tCO2e)",
            example = "{\"4\": 1000.0, \"6\": 500.0}")
    Map<Integer, BigDecimal> estimatedExcludedEmissions,

    @Schema(description = "제외 카테고리 사유 (카테고리번호 → 사유)",
            example = "{\"4\": \"중요성 낮음\", \"6\": \"해당 없음\"}")
    Map<Integer, String> exclusionReasons
) {
    public Scope3CoverageRequest {
        if (estimatedExcludedEmissions == null) estimatedExcludedEmissions = Map.of();
        if (exclusionReasons == null) exclusionReasons = Map.of();
    }
}
```

- [ ] **Step 3: Scope3CoverageResponse DTO 생성**

```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Scope 3 커버리지 보고서 응답")
public record Scope3CoverageResponse(
    @Schema(description = "보고서 ID") UUID id,
    @Schema(description = "법인 ID") UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "포함된 카테고리 번호 목록", example = "[1, 2, 11]")
    List<Integer> includedCategories,
    @Schema(description = "제외된 카테고리 번호 목록", example = "[4, 6]")
    List<Integer> excludedCategories,
    @Schema(description = "제외 카테고리 사유") Map<Integer, String> exclusionReasons,
    @Schema(description = "배출량 기반 커버리지 비율 (%)", example = "95.00")
    BigDecimal coveragePct,
    @Schema(description = "95% 임계값 충족 여부") boolean meets95PctThreshold,
    @Schema(description = "생성 시각") Instant generatedAt
) {}
```

- [ ] **Step 4: Scope3CoverageReportJpaEntity 생성**

```java
package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scope3_coverage_reports")
@Getter
@NoArgsConstructor
public class Scope3CoverageReportJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private int reportingYear;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String includedCategories;    // JSON: "[1,2,11]"

    @Column(columnDefinition = "TEXT")
    private String excludedCategories;    // JSON: "[4,6]" or null

    @Column(columnDefinition = "TEXT")
    private String exclusionReasons;      // JSON: {"4":"사유"} or null

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePct;

    @Column(nullable = false)
    private boolean meets95PctThreshold;

    @Column(nullable = false)
    private Instant generatedAt;

    @Builder
    public Scope3CoverageReportJpaEntity(UUID id, UUID tenantId, UUID entityId,
                                          int reportingYear,
                                          String includedCategories,
                                          String excludedCategories,
                                          String exclusionReasons,
                                          BigDecimal coveragePct,
                                          boolean meets95PctThreshold) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.reportingYear = reportingYear;
        this.includedCategories = includedCategories;
        this.excludedCategories = excludedCategories;
        this.exclusionReasons = exclusionReasons;
        this.coveragePct = coveragePct;
        this.meets95PctThreshold = meets95PctThreshold;
        this.generatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Scope3CoverageReportRepository 생성 (Append-only)**

```java
package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

// Append-only: 커버리지 보고서는 INSERT-only (P1 재현성 원칙, 08-persistence.md)
public interface Scope3CoverageReportRepository
        extends Repository<Scope3CoverageReportJpaEntity, UUID> {

    Scope3CoverageReportJpaEntity save(Scope3CoverageReportJpaEntity entity);

    Optional<Scope3CoverageReportJpaEntity>
        findTopByTenantIdAndEntityIdAndReportingYearOrderByGeneratedAtDesc(
            UUID tenantId, UUID entityId, int reportingYear);
}
```

- [ ] **Step 6: DefaultScope3Service 구현**

```java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.EmissionRecordResponse;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageRequest;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageResponse;
import ai.claudecode.esgt2.ghg.api.Scope3Service;
import ai.claudecode.esgt2.ghg.domain.EmissionFactor;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.domain.EmissionRecord;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat1Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat2Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3Cat11Calculator;
import ai.claudecode.esgt2.ghg.domain.Scope3CoverageCalculator;
import ai.claudecode.esgt2.ghg.domain.Scope3CoverageReport;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordJpaEntity;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordMapper;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.ghg.infra.Scope3CoverageReportJpaEntity;
import ai.claudecode.esgt2.ghg.infra.Scope3CoverageReportRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultScope3Service implements Scope3Service {

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final Scope3CoverageReportRepository coverageReportRepository;
    private final EmissionFactorResolver emissionFactorResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT1_CALCULATED")
    public List<EmissionRecordResponse> calculateCat1(UUID tenantId, UUID entityId, int reportingYear) {
        return calculateScope3(tenantId, entityId, reportingYear,
            List.of("SCOPE3_CAT1"), 1, "CAT1");
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT2_CALCULATED")
    public List<EmissionRecordResponse> calculateCat2(UUID tenantId, UUID entityId, int reportingYear) {
        return calculateScope3(tenantId, entityId, reportingYear,
            List.of("SCOPE3_CAT2"), 2, "CAT2");
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_CAT11_CALCULATED")
    public List<EmissionRecordResponse> calculateCat11(UUID tenantId, UUID entityId, int reportingYear) {
        var activityDataList = activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
                tenantId, entityId, reportingYear, List.of("SCOPE3_CAT11"));

        Map<String, EmissionFactor> factorCache = new HashMap<>();
        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(),
                    ad.getCountryCode(), date));

            BigDecimal emission = Scope3Cat11Calculator.computeAnnualEmission(
                ad.getQuantity(), factor.factorValue(), ad.getLifetimeYears());

            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", 11, "CO2E",
                factor.id(), emission);
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    @Override
    @Transactional
    @Auditable(action = "SCOPE3_COVERAGE_GENERATED")
    public Scope3CoverageResponse generateCoverageReport(UUID tenantId, UUID entityId,
            int reportingYear, Scope3CoverageRequest request) {

        List<EmissionRecordJpaEntity> scope3Records =
            emissionRecordRepository.findScope3ByTenantIdAndEntityIdAndReportingYear(
                tenantId, entityId, reportingYear);

        // 카테고리별 배출량 합산
        Map<Integer, BigDecimal> includedEmissions = scope3Records.stream()
            .filter(r -> r.getScope3Category() != null)
            .collect(Collectors.groupingBy(
                EmissionRecordJpaEntity::getScope3Category,
                Collectors.reducing(BigDecimal.ZERO,
                    EmissionRecordJpaEntity::getRawEmission, BigDecimal::add)));

        Scope3CoverageReport domain = Scope3CoverageCalculator.calculate(
            tenantId, entityId, reportingYear,
            includedEmissions,
            request.estimatedExcludedEmissions(),
            request.exclusionReasons());

        var entity = Scope3CoverageReportJpaEntity.builder()
            .id(domain.id())
            .tenantId(domain.tenantId())
            .entityId(domain.entityId())
            .reportingYear(domain.reportingYear())
            .includedCategories(toJson(domain.includedCategories()))
            .excludedCategories(domain.excludedCategories().isEmpty()
                ? null : toJson(domain.excludedCategories()))
            .exclusionReasons(domain.exclusionReasons().isEmpty()
                ? null : toJson(domain.exclusionReasons()))
            .coveragePct(domain.coveragePct())
            .meets95PctThreshold(domain.meets95PctThreshold())
            .build();

        var saved = coverageReportRepository.save(entity);
        return toCoverageResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Scope3CoverageResponse getCoverageReport(UUID tenantId, UUID entityId, int reportingYear) {
        return coverageReportRepository
            .findTopByTenantIdAndEntityIdAndReportingYearOrderByGeneratedAtDesc(
                tenantId, entityId, reportingYear)
            .map(this::toCoverageResponse)
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "커버리지 보고서를 찾을 수 없습니다: entityId=" + entityId + ", year=" + reportingYear));
    }

    // Cat.1/Cat.2 공통 계산 흐름
    private List<EmissionRecordResponse> calculateScope3(UUID tenantId, UUID entityId,
            int reportingYear, List<String> categories, int scope3CategoryNum, String tag) {

        var activityDataList = activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
                tenantId, entityId, reportingYear, categories);

        Map<String, EmissionFactor> factorCache = new HashMap<>();
        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(),
                    ad.getCountryCode(), date));

            BigDecimal emission = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", scope3CategoryNum, "CO2E",
                factor.id(), EmissionCalculatorDelegate.compute(ad.getQuantity(), factor.factorValue()))
                .rawEmission();

            // 데이터 품질 자동 결정 (Cat.1 전용 — Cat.2는 기존 dataQuality 유지)
            // Note: Cat.2는 자본재로 품질 결정 로직이 Cat.1과 동일하게 적용됨
            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", scope3CategoryNum, "CO2E",
                factor.id(), computeEmission(ad, factor));
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }

    private BigDecimal computeEmission(ActivityDataJpaEntity ad, EmissionFactor factor) {
        // EmissionCalculator 경유 — 06-emission-calculation.md BigDecimal 전용 정책
        return EmissionCalculator.computeEmission(ad.getQuantity(), factor.factorValue());
    }

    private EmissionRecordResponse toEmissionRecordResponse(EmissionRecordJpaEntity e) {
        return new EmissionRecordResponse(
            e.getId(), e.getEntityId(),
            e.getActivityDataId(), e.getReportingYear(),
            e.getScope(), e.getGhgType(), e.getEmissionFactorId(),
            e.getRawEmission(), e.isConsolidated(), e.getCalculatedAt());
    }

    private Scope3CoverageResponse toCoverageResponse(Scope3CoverageReportJpaEntity e) {
        return new Scope3CoverageResponse(
            e.getId(), e.getEntityId(), e.getReportingYear(),
            fromJson(e.getIncludedCategories(), Integer.class),
            e.getExcludedCategories() == null ? List.of()
                : fromJson(e.getExcludedCategories(), Integer.class),
            e.getExclusionReasons() == null ? Map.of()
                : fromJsonMap(e.getExclusionReasons()),
            e.getCoveragePct(), e.isMeets95PctThreshold(), e.getGeneratedAt());
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private <T> List<T> fromJson(String json, Class<T> elementType) {
        try {
            var type = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, elementType);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) { return List.of(); }
    }

    private Map<Integer, String> fromJsonMap(String json) {
        try {
            var type = objectMapper.getTypeFactory()
                .constructMapType(Map.class, Integer.class, String.class);
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) { return Map.of(); }
    }
}
```

> **주의**: `calculateScope3()` 내부 `EmissionCalculatorDelegate.compute()` 참조를 제거하고 `computeEmission()` 단일 메서드로 정리합니다. 위 코드에 중복 로직이 남아 있으므로 아래와 같이 정리합니다.

실제 `calculateScope3()` 메서드를 아래로 교체합니다:

```java
    private List<EmissionRecordResponse> calculateScope3(UUID tenantId, UUID entityId,
            int reportingYear, List<String> categories, int scope3CategoryNum, String tag) {

        var activityDataList = activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYearAndCategoryIn(
                tenantId, entityId, reportingYear, categories);

        Map<String, EmissionFactor> factorCache = new HashMap<>();
        return activityDataList.stream().map(ad -> {
            var date = LocalDate.of(ad.getReportingYear(), 1, 1);
            var cacheKey = ad.getCategory() + "|" + ad.getSubCategory() + "|" + ad.getCountryCode() + "|" + date;
            var factor = factorCache.computeIfAbsent(cacheKey, k ->
                emissionFactorResolver.resolveAt(ad.getCategory(), ad.getSubCategory(),
                    ad.getCountryCode(), date));

            var domain = EmissionRecord.calculate(
                ad.getTenantId(), ad.getEntityId(), ad.getId(),
                ad.getReportingYear(), "SCOPE3", scope3CategoryNum, "CO2E",
                factor.id(), computeEmission(ad, factor));
            var saved = emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
            return toEmissionRecordResponse(saved);
        }).toList();
    }
```

- [ ] **Step 7: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: feat: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/api/Scope3Service.java \
        src/main/java/ai/claudecode/esgt2/ghg/api/Scope3CoverageRequest.java \
        src/main/java/ai/claudecode/esgt2/ghg/api/Scope3CoverageResponse.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/Scope3CoverageReportJpaEntity.java \
        src/main/java/ai/claudecode/esgt2/ghg/infra/Scope3CoverageReportRepository.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultScope3Service.java
git commit -m "feat: Scope3Service 인터페이스 + DefaultScope3Service + JPA 인프라 구현"
```

---

## Task 9: GhgController Scope3 엔드포인트 추가

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/api/GhgController.java`

- [ ] **Step 1: GhgController에 Scope3Service 주입 + 5개 엔드포인트 추가**

`GhgController` 클래스 필드에 `Scope3Service` 추가:

```java
    private final Scope3Service scope3Service;
```

그리고 기존 엔드포인트 아래에 다음 메서드들을 추가:

```java
    @Operation(summary = "Scope 3 Cat.1 배출량 산출",
               description = "구매재화·서비스 지출 기반 Scope 3 Cat.1 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping("/entities/{entityId}/scope3/cat1/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat1(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat1(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 Cat.2 배출량 산출",
               description = "자본재 취득액 기반 Scope 3 Cat.2 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @PostMapping("/entities/{entityId}/scope3/cat2/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat2(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat2(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 Cat.11 배출량 산출",
               description = "판매제품 사용 생애주기 귀속 방식으로 Scope 3 Cat.11 배출량을 산출합니다.")
    @ApiResponse(responseCode = "201", description = "산출 성공")
    @PostMapping("/entities/{entityId}/scope3/cat11/calculations")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<List<EmissionRecordResponse>> calculateScope3Cat11(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.status(201)
            .body(scope3Service.calculateCat11(auth.getTenantId(), entityId, reportingYear));
    }

    @Operation(summary = "Scope 3 커버리지 보고서 생성",
               description = "배출량 기반 95% 임계값 판단. 제외 카테고리 추정치를 요청 본문에 포함.")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @PostMapping("/entities/{entityId}/scope3/coverage-report")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<Scope3CoverageResponse> generateCoverageReport(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestBody @Valid Scope3CoverageRequest request) {
        return ResponseEntity.status(201)
            .body(scope3Service.generateCoverageReport(
                auth.getTenantId(), entityId, request.reportingYear(), request));
    }

    @Operation(summary = "Scope 3 커버리지 보고서 조회",
               description = "가장 최근 생성된 Scope 3 커버리지 보고서를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "보고서 없음")
    @GetMapping("/entities/{entityId}/scope3/coverage-report")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<Scope3CoverageResponse> getCoverageReport(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @Parameter(description = "보고 연도", example = "2025") @RequestParam int reportingYear) {
        return ResponseEntity.ok(
            scope3Service.getCoverageReport(auth.getTenantId(), entityId, reportingYear));
    }
```

- [ ] **Step 2: 전체 빌드 + 테스트 실행**

```bash
./gradlew test
./gradlew test --tests "*ModularityTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: feat: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/api/GhgController.java
git commit -m "feat: GhgController Scope3 엔드포인트 5개 추가 (Cat1/2/11 산출 + 커버리지 생성/조회)"
```

---

## Task 10: 통합 테스트 작성 + 전체 검증

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/Scope3IntegrationTest.java`

- [ ] **Step 1: Scope3IntegrationTest 생성**

```java
package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.audit.internal.OutboxProcessingService;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageRequest;
import ai.claudecode.esgt2.ghg.api.Scope3CoverageResponse;
import ai.claudecode.esgt2.ghg.api.Scope3Service;
import ai.claudecode.esgt2.ghg.infra.EmissionFactorLoader;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Scope3IntegrationTest extends AbstractIntegrationTest {

    @Autowired private Scope3Service scope3Service;
    @Autowired private GhgService ghgService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private EmissionFactorLoader emissionFactorLoader;
    @Autowired private OutboxProcessingService outboxProcessingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityId;

    @BeforeEach
    void setup() throws Exception {
        // 테스트 데이터 정리 (FK 순서 역순)
        jdbcTemplate.execute("DELETE FROM scope3_coverage_reports WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000004'");
        jdbcTemplate.execute("DELETE FROM emission_factors");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000004', 'TEST4', '테스트법인4', 'KR') " +
            "ON CONFLICT DO NOTHING");
        // 서비스 레이어는 tenantId 파라미터로 직접 동작 (@PreAuthorize는 컨트롤러 레이어에서만 적용)

        // 배출계수 로드 (SCOPE3 전용 YAML)
        emissionFactorLoader.load(new ClassPathResource("factors/scope3-test-factors.yaml"));

        // 법인 생성
        var entityResp = entityManagementService.createEntity(
            TENANT_ID, new CreateEntityRequest("테스트법인4", "KR", LegalEntityType.PARENT.name()), ACTOR_ID);
        entityId = entityResp.id();
    }

    @Test
    void Cat1_지출기반_배출량_산출_후_emission_record_저장() {
        // Cat.1 활동 데이터 등록 (지출 10,000 KRW, 배출계수 0.0005 tCO2e/KRW)
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("10000"), "KRW", "KR", "MANUAL", null, null));

        var results = scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("5.000000"));
        assertThat(results.get(0).scope()).isEqualTo("SCOPE3");

        // AuditLog 확인
        outboxProcessingService.processNow();
        int logCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action = 'SCOPE3_CAT1_CALCULATED'", Integer.class);
        assertThat(logCount).isGreaterThan(0);
    }

    @Test
    void Cat2_자본재_배출량_산출_후_emission_record_저장() {
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT2", null,
            new BigDecimal("500000"), "KRW", "KR", "MANUAL", null, null));

        var results = scope3Service.calculateCat2(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("150.000000"));
    }

    @Test
    void Cat11_생애주기_귀속_배출량_산출() {
        // TV 1,000대, 배출계수 0.5 tCO2e/대, 사용기간 8년 → 62.5 tCO2e/year
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT11", "TV",
            new BigDecimal("1000"), "units", "KR", "MANUAL", null, 8));

        var results = scope3Service.calculateCat11(TENANT_ID, entityId, 2025);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rawEmission()).isEqualByComparingTo(new BigDecimal("62.500000"));
    }

    @Test
    void 커버리지_보고서_생성_후_95퍼센트_달성_확인() {
        // Cat.1 배출량 등록 후 산출
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("10000"), "KRW", "KR", "MANUAL", null, null));
        scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        // Cat.4 추정 배출량 0.263158... → 95% 초과 달성
        // includedTotal=5, estimatedExcluded=0.263158 → 5/(5+0.263158)=95.0%
        var request = new Scope3CoverageRequest(2025,
            Map.of(4, new BigDecimal("0.263158")),
            Map.of(4, "중요성 평가 결과 제외"));

        Scope3CoverageResponse response = scope3Service.generateCoverageReport(
            TENANT_ID, entityId, 2025, request);

        assertThat(response.meets95PctThreshold()).isTrue();
        assertThat(response.includedCategories()).contains(1);
        assertThat(response.excludedCategories()).contains(4);

        // 조회 테스트
        Scope3CoverageResponse fetched = scope3Service.getCoverageReport(TENANT_ID, entityId, 2025);
        assertThat(fetched.id()).isEqualTo(response.id());
    }

    @Test
    void 커버리지_보고서_미달_시_meets_threshold_false() {
        // Cat.1 배출량 700, 추정 제외 300 → 70%
        ghgService.createActivityData(TENANT_ID, entityId, new CreateActivityDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("1400000"), "KRW", "KR", "MANUAL", null, null));
        scope3Service.calculateCat1(TENANT_ID, entityId, 2025);

        var request = new Scope3CoverageRequest(2025,
            Map.of(4, new BigDecimal("300")),
            Map.of(4, "데이터 없음"));

        Scope3CoverageResponse response = scope3Service.generateCoverageReport(
            TENANT_ID, entityId, 2025, request);

        assertThat(response.meets95PctThreshold()).isFalse();
    }
}
```

- [ ] **Step 2: 통합 테스트용 Scope3 배출계수 YAML 파일 생성**

`src/test/resources/factors/scope3-test-factors.yaml` 생성:

```yaml
source: "SPEND_BASED"
reporting_year: 2025
factors:
  - category: "SCOPE3_CAT1"
    sub_category: "ELECTRONICS"
    country_code: "KR"
    gwp: "AR6"
    factor_value: "0.00050000"
    unit: "tCO2e/KRW"
    effective_from: "2025-01-01"
  - category: "SCOPE3_CAT2"
    sub_category: null
    country_code: "KR"
    gwp: "AR6"
    factor_value: "0.00030000"
    unit: "tCO2e/KRW"
    effective_from: "2025-01-01"
  - category: "SCOPE3_CAT11"
    sub_category: "TV"
    country_code: "KR"
    gwp: "AR6"
    factor_value: "0.50000000"
    unit: "tCO2e/unit"
    effective_from: "2025-01-01"
```

- [ ] **Step 3: 통합 테스트 실행**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ghg.Scope3IntegrationTest"
```

Expected: `BUILD SUCCESSFUL` (5건 통과)

- [ ] **Step 4: 전체 테스트 실행 + ModularityTest**

```bash
./gradlew test
./gradlew test --tests "*ModularityTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: test: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/Scope3IntegrationTest.java \
        src/test/resources/factors/scope3-test-factors.yaml
git commit -m "test: Scope3 통합 테스트 — Cat.1/2/11 계산 + 커버리지 보고서 (5건)"
```

---

## Task 11: task.md Phase 5 완료 체크

**Files:**
- Modify: `docs/task.md`

- [ ] **Step 1: task.md Phase 5 태스크 상태를 DONE으로 갱신**

T-5-01 ~ T-5-12 모두 `TODO` → `DONE`으로 변경. 비고란에 완료 일자 및 테스트 수 기록.

- [ ] **Step 2: code-review.md에 Phase 5 리뷰 기록 추가**

`docs/code-review.md` 2. 리뷰 이력에 Phase 5 리뷰 섹션 추가.

- [ ] **Step 3: 최종 커밋**

```bash
git add docs/task.md docs/code-review.md
git commit -m "chore: Phase 5 Scope 3 계산 엔진 완료 — task.md / code-review.md 갱신"
```

---

## 자가 검토 (Spec Coverage)

| 스펙 요구사항 | 구현 태스크 |
|---|---|
| T-5-01 Cat.1 지출 기반 계산 정확도 테스트 | Task 1 |
| T-5-02 Scope3Cat1Calculator | Task 2 |
| T-5-03 데이터 품질 점수 자동 부여 | Task 2 (deriveDataQuality) |
| T-5-04 Cat.2 자본재 계산 테스트 | Task 1 |
| T-5-05 Scope3Cat2Calculator | Task 2 |
| T-5-06 Cat.11 다년도 귀속 테스트 | Task 3 |
| T-5-07 Scope3Cat11Calculator | Task 4 |
| T-5-08 V18__scope3_tables.sql | Task 7 |
| T-5-09 95% 임계값 판단 테스트 | Task 5 |
| T-5-10 Scope3CoverageCalculator | Task 6 |
| T-5-11 GET/POST coverage-report API | Task 8, 9 |
| T-5-12 Category 16 DB 스키마 준비 | Task 7 (CHECK 1~16) |
