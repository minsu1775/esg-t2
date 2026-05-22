# Phase 6-B 정정·재공시 & Formula DSL 배포 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 활동 데이터 정정(새 버전 INSERT·원본 ARCHIVED)·재산출 자동 트리거·버전 이력·diff API와 Formula YAML 등록·버전 관리·영향 조회 API를 구축한다.

**Architecture:** ActivityData 정정은 원본을 ARCHIVED 처리 후 새 레코드를 INSERT(P1 불변성 준수). EmissionRecordRepository는 append-only이므로 삭제 없이 correctedActivityDataId로 새 배출량 기록. FormulaVersion은 ghg 모듈 내 별도 테이블로 관리하며 test_cases 게이트를 통과해야만 ACTIVE 등록된다.

**Tech Stack:** Spring Boot 4 / Spring Modulith / JPA+Flyway / ApplicationEventPublisher / FormulaLoader(기존)

---

## 파일 구조

| 역할 | 경로 | 신규/수정 |
|---|---|---|
| DB 마이그레이션 — 정정 컬럼 | `db/migration/V20__activity_data_correction.sql` | 신규 |
| DB 마이그레이션 — formula_versions | `db/migration/V21__formula_versions.sql` | 신규 |
| 정정 커맨드 | `ghg/domain/CorrectActivityDataCommand.java` | 신규 |
| ActivityData 도메인 | `ghg/domain/ActivityData.java` | 수정 (correct() 팩토리 추가) |
| JPA 엔티티 | `ghg/infra/ActivityDataJpaEntity.java` | 수정 (archive() + 정정 컬럼) |
| 매퍼 | `ghg/infra/ActivityDataMapper.java` | 수정 |
| 레포지토리 | `ghg/infra/ActivityDataRepository.java` | 수정 (findByCorrectionOf, findByTenantIdAndCategory) |
| 정정 이벤트 | `shared/event/ActivityDataCorrectedEvent.java` | 신규 |
| 이벤트 핸들러 | `ghg/internal/ActivityDataEventHandler.java` | 신규 |
| GhgService 인터페이스 | `ghg/api/GhgService.java` | 수정 |
| 정정 요청 DTO | `ghg/api/CorrectActivityDataRequest.java` | 신규 |
| 버전 이력 응답 DTO | `ghg/api/ActivityDataVersionResponse.java` | 신규 |
| Diff 응답 DTO | `ghg/api/ActivityDataDiffResponse.java` | 신규 |
| DefaultGhgService | `ghg/internal/DefaultGhgService.java` | 수정 |
| GhgController | `ghg/api/GhgController.java` | 수정 (신규 엔드포인트 + BUG-P6B-01 수정) |
| Formula 도메인 | `ghg/domain/FormulaVersion.java` | 신규 |
| Formula JPA 엔티티 | `ghg/infra/FormulaVersionJpaEntity.java` | 신규 |
| Formula 레포지토리 | `ghg/infra/FormulaVersionRepository.java` | 신규 |
| FormulaVersionService | `ghg/api/FormulaVersionService.java` | 신규 |
| Formula 응답/요청 DTO | `ghg/api/FormulaVersionResponse.java`, `RegisterFormulaRequest.java`, `FormulaImpactResponse.java` | 신규 |
| DefaultFormulaVersionService | `ghg/internal/DefaultFormulaVersionService.java` | 신규 |
| FormulaController | `ghg/api/FormulaController.java` | 신규 |
| 도메인 유닛 테스트 | `test/.../ghg/domain/ActivityDataCorrectionDomainTest.java` | 신규 |
| Formula 도메인 테스트 | `test/.../ghg/domain/FormulaVersionDomainTest.java` | 신규 |
| 정정 통합 테스트 | `test/.../ghg/CorrectionIntegrationTest.java` | 신규 |
| Formula 통합 테스트 | `test/.../ghg/FormulaVersionIntegrationTest.java` | 신규 |

---

## Task 1: DB 마이그레이션

**Files:**
- Create: `src/main/resources/db/migration/V20__activity_data_correction.sql`
- Create: `src/main/resources/db/migration/V21__formula_versions.sql`

- [ ] **Step 1: V20 작성**

```sql
-- V20__activity_data_correction.sql
-- 정정 컬럼 추가 (P1: 새 버전 INSERT 패턴)
ALTER TABLE activity_data
  ADD COLUMN correction_of UUID REFERENCES activity_data(id),
  ADD COLUMN correction_reason TEXT;

-- ARCHIVED 상태: 정정된 원본 레코드 표식
-- 기존 CHECK 제약이 있다면 제거 후 재등록 (H2/PG 공통 문법)
ALTER TABLE activity_data DROP CONSTRAINT IF EXISTS activity_data_status_check;
ALTER TABLE activity_data ADD CONSTRAINT activity_data_status_check
  CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','ARCHIVED'));

CREATE INDEX idx_activity_data_correction_of ON activity_data(correction_of)
  WHERE correction_of IS NOT NULL;
```

- [ ] **Step 2: V21 작성**

```sql
-- V21__formula_versions.sql
CREATE TABLE formula_versions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50) NOT NULL,
    version      VARCHAR(20) NOT NULL,
    expression   TEXT        NOT NULL,
    ghg_category VARCHAR(50),
    yaml_content TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    activated_by UUID,
    created_at   TIMESTAMPTZ NOT NULL  DEFAULT NOW(),
    UNIQUE (code, version),
    CONSTRAINT formula_versions_status_check
        CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_formula_versions_code_status ON formula_versions(code, status);
```

- [ ] **Step 3: 마이그레이션 적용 확인**

```
./gradlew flywayInfo
```

Expected: V20, V21이 Pending 상태로 표시됨.

- [ ] **Step 4: 커밋**

```
git add src/main/resources/db/migration/V20__activity_data_correction.sql \
        src/main/resources/db/migration/V21__formula_versions.sql
git commit -m "feat: V20 정정 컬럼 + ARCHIVED 상태 + V21 formula_versions 마이그레이션"
```

---

## Task 2: 정정 도메인 유닛 테스트 작성 (RED) — T-6B-01, T-6B-02

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/domain/ActivityDataCorrectionDomainTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityDataCorrectionDomainTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();

    private ActivityData makeOriginal() {
        return ActivityData.create(new CreateActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
    }

    // T-6B-01: 정정 → 새 UUID, 원본 ID 참조, 원본 quantity 불변
    @Test
    void 정정_시_새_UUID_생성_correctionOf_원본_참조() {
        var original = makeOriginal();
        var cmd = new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null,
            "계량기 재측정 결과 반영");

        var corrected = ActivityData.correct(original, cmd);

        assertThat(corrected.id()).isNotEqualTo(original.id());
        assertThat(corrected.correctionOf()).isEqualTo(original.id());
        assertThat(corrected.correctionReason()).isEqualTo("계량기 재측정 결과 반영");
        assertThat(corrected.quantity()).isEqualByComparingTo(new BigDecimal("1200"));
        // 원본 quantity 불변
        assertThat(original.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    // T-6B-02: 정정 사유 빈 문자열 → 예외
    @Test
    void 정정_사유_빈_문자열_시_예외() {
        assertThatThrownBy(() -> new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("정정 사유");
    }

    // T-6B-02: 정정 사유 null → 예외
    @Test
    void 정정_사유_null_시_예외() {
        assertThatThrownBy(() -> new CorrectActivityDataCommand(
            TENANT_ID, ENTITY_ID, 2025, "SCOPE1_FUEL", "GAS",
            new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("정정 사유");
    }
}
```

- [ ] **Step 2: 실패 확인 (RED)**

```
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.ActivityDataCorrectionDomainTest"
```

Expected: FAIL — `CorrectActivityDataCommand` 클래스 없음, `ActivityData.correct()` 없음.

---

## Task 3: 정정 도메인 구현 (GREEN) — T-6B-01, T-6B-02

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/CorrectActivityDataCommand.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/domain/ActivityData.java`

- [ ] **Step 1: CorrectActivityDataCommand 작성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record CorrectActivityDataCommand(
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
    Integer lifetimeYears,
    String correctionReason
) {
    public CorrectActivityDataCommand {
        if (tenantId == null) throw new IllegalArgumentException("tenantId 필수");
        if (entityId == null) throw new IllegalArgumentException("entityId 필수");
        if (category == null || category.isBlank()) throw new IllegalArgumentException("category 필수");
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("활동량은 0 이상이어야 합니다");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit 필수");
        if (countryCode == null || countryCode.isBlank()) throw new IllegalArgumentException("countryCode 필수");
        if (correctionReason == null || correctionReason.isBlank())
            throw new IllegalArgumentException("정정 사유는 필수입니다");
    }
}
```

- [ ] **Step 2: ActivityData에 correctionOf·correctionReason 필드 및 correct() 팩토리 추가**

기존 `ActivityData.java`의 record 필드 목록과 `create()` 메서드를 유지하면서 아래와 같이 수정한다.

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
    Integer lifetimeYears,
    UUID correctionOf,        // 정정 원본 ID (null = 최초 등록)
    String correctionReason   // 정정 사유 (null = 최초 등록)
) {
    /** 최초 등록 팩토리 */
    public static ActivityData create(CreateActivityDataCommand cmd) {
        String stdUnit = UnitConverter.standardUnitFor(cmd.unit());
        BigDecimal stdValue = null;
        if (stdUnit != null && !stdUnit.equals(cmd.unit())) {
            try {
                stdValue = UnitConverter.convert(cmd.quantity(), cmd.unit(), stdUnit);
            } catch (IllegalArgumentException ignored) {}
        } else if (stdUnit != null) {
            stdValue = cmd.quantity();
        }
        String resolvedQuality = "SCOPE3_CAT1".equals(cmd.category())
            ? Scope3Cat1Calculator.deriveDataQuality(cmd.dataSource())
            : (cmd.dataQuality() != null ? cmd.dataQuality() : "AVERAGE_DATA");

        return new ActivityData(
            UUID.randomUUID(),
            cmd.tenantId(), cmd.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : "MANUAL",
            resolvedQuality,
            stdValue, stdUnit,
            cmd.lifetimeYears(),
            null, null   // correctionOf, correctionReason
        );
    }

    /** 정정 팩토리 — 원본을 참조하는 새 레코드 생성 (P1: INSERT-only) */
    public static ActivityData correct(ActivityData original, CorrectActivityDataCommand cmd) {
        // cmd compact constructor에서 correctionReason 검증됨
        String stdUnit = UnitConverter.standardUnitFor(cmd.unit());
        BigDecimal stdValue = null;
        if (stdUnit != null && !stdUnit.equals(cmd.unit())) {
            try {
                stdValue = UnitConverter.convert(cmd.quantity(), cmd.unit(), stdUnit);
            } catch (IllegalArgumentException ignored) {}
        } else if (stdUnit != null) {
            stdValue = cmd.quantity();
        }
        String resolvedQuality = "SCOPE3_CAT1".equals(cmd.category())
            ? Scope3Cat1Calculator.deriveDataQuality(cmd.dataSource())
            : (cmd.dataQuality() != null ? cmd.dataQuality() : original.dataQuality());

        return new ActivityData(
            UUID.randomUUID(),
            original.tenantId(), original.entityId(),
            cmd.reportingYear(), cmd.category(), cmd.subCategory(),
            cmd.quantity(), cmd.unit(), cmd.countryCode(),
            cmd.dataSource() != null ? cmd.dataSource() : original.dataSource(),
            resolvedQuality,
            stdValue, stdUnit,
            cmd.lifetimeYears(),
            original.id(),          // correctionOf = 원본 ID
            cmd.correctionReason()
        );
    }
}
```

- [ ] **Step 3: 테스트 통과 확인 (GREEN)**

```
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.ActivityDataCorrectionDomainTest"
```

Expected: 3 tests PASSED.

- [ ] **Step 4: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/domain/CorrectActivityDataCommand.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/ActivityData.java \
        src/test/java/ai/claudecode/esgt2/ghg/domain/ActivityDataCorrectionDomainTest.java
git commit -m "test: ActivityData 정정 도메인 유닛 테스트 + correct() 팩토리 (T-6B-01, T-6B-02)"
```

---

## Task 4: JPA 인프라 수정 — ActivityDataJpaEntity + Mapper + Repository

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataJpaEntity.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataMapper.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataRepository.java`

- [ ] **Step 1: ActivityDataJpaEntity에 정정 필드 + archive() 추가**

기존 파일에서 `lifetimeYears` 필드 이후에 두 컬럼을 추가하고, `archive()` 상태 전이 메서드를 추가한다.

```java
// 기존 필드에 추가 (lifetimeYears 아래)
private UUID correctionOf;    // 정정 원본 ID (null = 최초 등록)
private String correctionReason;

// 기존 @Builder 생성자 파라미터 추가
@Builder
public ActivityDataJpaEntity(UUID id, UUID tenantId, UUID entityId,
                              int reportingYear, String category, String subCategory,
                              BigDecimal quantity, String unit, String countryCode,
                              BigDecimal standardValue, String standardUnit,
                              String dataSource, String dataQuality,
                              Integer lifetimeYears,
                              UUID correctionOf, String correctionReason) {  // NEW
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
    this.lifetimeYears = lifetimeYears;
    this.correctionOf = correctionOf;         // NEW
    this.correctionReason = correctionReason; // NEW
    this.status = "DRAFT";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
}

// 기존 submit/approve/reject 아래에 추가
/** ARCHIVED 상태 전이 — 정정된 원본 레코드 표식 (P1: INSERT-only 정정 패턴) */
public void archive() {
    if ("ARCHIVED".equals(this.status)) throw new IllegalStateException("이미 보관된 데이터입니다");
    this.status = "ARCHIVED";
    this.updatedAt = Instant.now();
}
```

- [ ] **Step 2: ActivityDataMapper에 correctionOf·correctionReason 추가**

```java
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
        .lifetimeYears(domain.lifetimeYears())
        .correctionOf(domain.correctionOf())        // NEW
        .correctionReason(domain.correctionReason()) // NEW
        .build();
}
```

- [ ] **Step 3: ActivityDataRepository에 쿼리 메서드 추가**

```java
// 버전 이력 조회용 (correctionOf = 원본 ID)
List<ActivityDataJpaEntity> findByCorrectionOfAndTenantId(UUID correctionOf, UUID tenantId);

// Formula 영향 조회용 (특정 카테고리의 활동 데이터 전체)
List<ActivityDataJpaEntity> findByTenantIdAndCategory(UUID tenantId, String category);
```

- [ ] **Step 4: 빌드 확인**

```
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/infra/
git commit -m "feat: ActivityDataJpaEntity archive() + 정정 컬럼 + Mapper/Repository 확장"
```

---

## Task 5: 정정 API — GhgService + DefaultGhgService + GhgController (T-6B-03)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/CorrectActivityDataRequest.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/ActivityDataVersionResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/ActivityDataDiffResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/shared/event/ActivityDataCorrectedEvent.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/api/GhgService.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultGhgService.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/api/GhgController.java`

- [ ] **Step 1: DTO 및 이벤트 파일 작성**

`CorrectActivityDataRequest.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

@Schema(description = "활동 데이터 정정 요청")
public record CorrectActivityDataRequest(
    @Schema(description = "보고 연도") @NotNull @Min(2020) int reportingYear,
    @Schema(description = "GHG 카테고리") @NotBlank String category,
    @Schema(description = "세부 카테고리") String subCategory,
    @Schema(description = "정정 활동량") @NotNull @PositiveOrZero BigDecimal quantity,
    @Schema(description = "단위") @NotBlank String unit,
    @Schema(description = "국가 코드") @NotBlank String countryCode,
    @Schema(description = "데이터 출처") String dataSource,
    @Schema(description = "데이터 품질") String dataQuality,
    @Schema(description = "제품 사용기간 (Cat.11 전용)") Integer lifetimeYears,
    @Schema(description = "정정 사유 (필수)") @NotBlank String correctionReason
) {}
```

`ActivityDataVersionResponse.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "활동 데이터 버전 이력 항목")
public record ActivityDataVersionResponse(
    @Schema(description = "레코드 ID") UUID id,
    @Schema(description = "정정 원본 ID (null=최초)") UUID correctionOf,
    @Schema(description = "정정 사유") String correctionReason,
    @Schema(description = "활동량") BigDecimal quantity,
    @Schema(description = "단위") String unit,
    @Schema(description = "GHG 카테고리") String category,
    @Schema(description = "세부 카테고리") String subCategory,
    @Schema(description = "상태") String status,
    @Schema(description = "생성 시각") Instant createdAt
) {}
```

`ActivityDataDiffResponse.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "활동 데이터 정정 전·후 비교")
public record ActivityDataDiffResponse(
    @Schema(description = "원본 ID") UUID originalId,
    @Schema(description = "정정 후 ID") UUID correctedId,
    @Schema(description = "정정 사유") String correctionReason,
    @Schema(description = "원본 활동량") BigDecimal originalQuantity,
    @Schema(description = "정정 활동량") BigDecimal correctedQuantity,
    @Schema(description = "원본 단위") String originalUnit,
    @Schema(description = "정정 단위") String correctedUnit,
    @Schema(description = "원본 카테고리") String originalCategory,
    @Schema(description = "정정 카테고리") String correctedCategory,
    @Schema(description = "원본 생성 시각") Instant originalCreatedAt,
    @Schema(description = "정정 생성 시각") Instant correctedCreatedAt
) {}
```

`ActivityDataCorrectedEvent.java` (shared/event 패키지, 11-modulith-events.md 규칙):
```java
package ai.claudecode.esgt2.shared.event;

import java.util.UUID;

/** 활동 데이터 정정 이벤트 — 배출량 재산출 트리거 */
public record ActivityDataCorrectedEvent(
    UUID tenantId,
    UUID entityId,
    int reportingYear,
    UUID newActivityDataId
) {}
```

- [ ] **Step 2: GhgService 인터페이스에 메서드 추가**

```java
// GhgService.java 기존 메서드 뒤에 추가
/** 활동 데이터 정정 — 원본 ARCHIVED, 새 레코드 INSERT (T-6B-03) */
ActivityDataResponse correctActivityData(
    UUID tenantId, UUID actorId, UUID originalId, CorrectActivityDataRequest request);

/** 버전 이력 조회 — 원본 포함 모든 정정 이력 (T-6B-05) */
List<ActivityDataVersionResponse> findVersionHistory(UUID tenantId, UUID activityDataId);

/** 정정 전·후 비교 (T-6B-06) */
ActivityDataDiffResponse findDiff(UUID tenantId, UUID correctedId);
```

- [ ] **Step 3: DefaultGhgService에 ApplicationEventPublisher 주입 및 correctActivityData() 구현**

`DefaultGhgService.java` 클래스 레벨에 `ApplicationEventPublisher` 필드 추가:
```java
// 기존 필드 다음에 추가
private final ApplicationEventPublisher eventPublisher;
```

`correctActivityData()` 메서드 추가:
```java
@Override
@Transactional
@Auditable(action = "ACTIVITY_DATA_CORRECTED")
public ActivityDataResponse correctActivityData(
        UUID tenantId, UUID actorId, UUID originalId, CorrectActivityDataRequest request) {

    // 1. 원본 조회
    var originalEntity = activityDataRepository.findByIdAndTenantId(originalId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("activity_data", originalId));

    // 2. 커맨드 생성 — correctionReason 검증은 CorrectActivityDataCommand 생성자에서 수행
    CorrectActivityDataCommand cmd;
    try {
        cmd = new CorrectActivityDataCommand(
            tenantId, originalEntity.getEntityId(),
            request.reportingYear(), request.category(), request.subCategory(),
            request.quantity(), request.unit(), request.countryCode(),
            request.dataSource(), request.dataQuality(), request.lifetimeYears(),
            request.correctionReason());
    } catch (IllegalArgumentException e) {
        throw new EsgException(EsgErrorCode.VALIDATION_FAILED, e.getMessage());
    }

    // 3. 도메인 팩토리로 정정 레코드 생성
    var originalDomain = new ActivityData(
        originalEntity.getId(), originalEntity.getTenantId(), originalEntity.getEntityId(),
        originalEntity.getReportingYear(), originalEntity.getCategory(), originalEntity.getSubCategory(),
        originalEntity.getQuantity(), originalEntity.getUnit(), originalEntity.getCountryCode(),
        originalEntity.getDataSource(), originalEntity.getDataQuality(),
        originalEntity.getStandardValue(), originalEntity.getStandardUnit(),
        originalEntity.getLifetimeYears(),
        originalEntity.getCorrectionOf(), originalEntity.getCorrectionReason());

    var correctedDomain = ActivityData.correct(originalDomain, cmd);

    // 4. 원본 ARCHIVED
    originalEntity.archive();
    activityDataRepository.save(originalEntity);

    // 5. 정정 레코드 저장
    var savedEntity = activityDataRepository.save(ActivityDataMapper.toEntity(correctedDomain));

    // 6. 재산출 이벤트 발행 (T-6B-04: ActivityDataEventHandler가 수신)
    eventPublisher.publishEvent(new ActivityDataCorrectedEvent(
        tenantId, savedEntity.getEntityId(), savedEntity.getReportingYear(), savedEntity.getId()));

    return toActivityDataResponse(savedEntity);
}
```

`findVersionHistory()` 메서드:
```java
@Override
@Transactional(readOnly = true)
public List<ActivityDataVersionResponse> findVersionHistory(UUID tenantId, UUID activityDataId) {
    var target = activityDataRepository.findByIdAndTenantId(activityDataId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("activity_data", activityDataId));

    // 루트 ID 결정: correctionOf가 null이면 이미 루트, 아니면 correctionOf가 루트
    UUID rootId = target.getCorrectionOf() != null ? target.getCorrectionOf() : target.getId();

    var rootOpt = activityDataRepository.findByIdAndTenantId(rootId, tenantId);
    var corrections = activityDataRepository.findByCorrectionOfAndTenantId(rootId, tenantId);

    return rootOpt.map(root -> {
        var list = new java.util.ArrayList<ActivityDataJpaEntity>();
        list.add(root);
        list.addAll(corrections);
        return list.stream()
            .sorted(java.util.Comparator.comparing(ActivityDataJpaEntity::getCreatedAt))
            .map(this::toVersionResponse)
            .toList();
    }).orElse(List.of());
}
```

`findDiff()` 메서드:
```java
@Override
@Transactional(readOnly = true)
public ActivityDataDiffResponse findDiff(UUID tenantId, UUID correctedId) {
    var corrected = activityDataRepository.findByIdAndTenantId(correctedId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("activity_data", correctedId));
    if (corrected.getCorrectionOf() == null) {
        throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "정정 이력이 없는 데이터입니다");
    }
    var original = activityDataRepository.findByIdAndTenantId(corrected.getCorrectionOf(), tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("activity_data", corrected.getCorrectionOf()));

    return new ActivityDataDiffResponse(
        original.getId(), corrected.getId(),
        corrected.getCorrectionReason(),
        original.getQuantity(), corrected.getQuantity(),
        original.getUnit(), corrected.getUnit(),
        original.getCategory(), corrected.getCategory(),
        original.getCreatedAt(), corrected.getCreatedAt());
}
```

`toVersionResponse()` 헬퍼 메서드:
```java
private ActivityDataVersionResponse toVersionResponse(ActivityDataJpaEntity e) {
    return new ActivityDataVersionResponse(
        e.getId(), e.getCorrectionOf(), e.getCorrectionReason(),
        e.getQuantity(), e.getUnit(),
        e.getCategory(), e.getSubCategory(),
        e.getStatus(), e.getCreatedAt());
}
```

- [ ] **Step 4: GhgController에 정정·버전·diff 엔드포인트 추가 (BUG-P6B-01 수정 포함)**

기존 `GhgController`의 `@AuthenticationPrincipal JwtAuthentication auth` 파라미터를 모두 `Authentication authentication` + `var auth = (JwtAuthentication) authentication;` 캐스트 방식으로 수정 (BUG-P6B-01). 그리고 아래 3개 엔드포인트를 추가한다.

```java
// GhgController 상단 import 추가
import org.springframework.security.core.Authentication;
// @AuthenticationPrincipal 사용 제거

// 기존 메서드들 내부: @AuthenticationPrincipal JwtAuthentication auth → 아래 패턴으로 교체
// Authentication authentication 파라미터 → var auth = (JwtAuthentication) authentication;

// ─── 새 엔드포인트 3개 ───

@Operation(summary = "활동 데이터 정정",
           description = "원본을 ARCHIVED 처리하고 정정 데이터를 새 레코드로 INSERT합니다.")
@ApiResponse(responseCode = "201", description = "정정 성공")
@ApiResponse(responseCode = "400", description = "정정 사유 누락 또는 유효성 오류")
@ApiResponse(responseCode = "404", description = "원본 활동 데이터 없음")
@PostMapping("/activity-data/{activityDataId}/correct")
@PreAuthorize("hasRole('ESG_MANAGER')")
public ResponseEntity<ActivityDataResponse> correctActivityData(
        Authentication authentication,
        @PathVariable UUID activityDataId,
        @RequestBody @Valid CorrectActivityDataRequest request) {
    var auth = (JwtAuthentication) authentication;
    var response = ghgService.correctActivityData(
        auth.getTenantId(), auth.getPrincipal(), activityDataId, request);
    return ResponseEntity.status(201).body(response);
}

@Operation(summary = "활동 데이터 버전 이력 조회",
           description = "원본 포함 모든 정정 이력을 시간 순으로 반환합니다.")
@ApiResponse(responseCode = "200", description = "조회 성공")
@GetMapping("/activity-data/{activityDataId}/versions")
@PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
public ResponseEntity<List<ActivityDataVersionResponse>> findVersionHistory(
        Authentication authentication,
        @PathVariable UUID activityDataId) {
    var auth = (JwtAuthentication) authentication;
    return ResponseEntity.ok(ghgService.findVersionHistory(auth.getTenantId(), activityDataId));
}

@Operation(summary = "정정 전·후 비교",
           description = "정정된 레코드 ID를 받아 원본과 수치를 비교합니다.")
@ApiResponse(responseCode = "200", description = "조회 성공")
@ApiResponse(responseCode = "400", description = "정정 이력 없는 데이터")
@GetMapping("/activity-data/{activityDataId}/diff")
@PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
public ResponseEntity<ActivityDataDiffResponse> findDiff(
        Authentication authentication,
        @PathVariable UUID activityDataId) {
    var auth = (JwtAuthentication) authentication;
    return ResponseEntity.ok(ghgService.findDiff(auth.getTenantId(), activityDataId));
}
```

- [ ] **Step 5: import 목록 확인 후 빌드**

```
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL (누락 import가 있으면 추가: `ResourceNotFoundException`, `EsgException`, `EsgErrorCode`)

- [ ] **Step 6: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/ \
        src/main/java/ai/claudecode/esgt2/shared/event/ActivityDataCorrectedEvent.java
git commit -m "feat: 활동 데이터 정정 API + 버전 이력 + diff (T-6B-03, T-6B-05, T-6B-06)"
```

---

## Task 6: 재산출 자동 트리거 — ActivityDataEventHandler (T-6B-04)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/ActivityDataEventHandler.java`

- [ ] **Step 1: 이벤트 핸들러 작성**

```java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.domain.EmissionCalculator;
import ai.claudecode.esgt2.ghg.domain.EmissionFactorResolver;
import ai.claudecode.esgt2.ghg.domain.EmissionRecord;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordMapper;
import ai.claudecode.esgt2.ghg.infra.EmissionRecordRepository;
import ai.claudecode.esgt2.shared.event.ActivityDataCorrectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 활동 데이터 정정 이벤트 수신 → 정정된 레코드에 대한 배출량 재산출.
 * EmissionRecordRepository는 append-only이므로 기존 레코드 삭제 없이 새 레코드 INSERT.
 * @Async 미사용 → @Transactional 단독 선언 가능 (05-async-concurrency.md).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ActivityDataEventHandler {

    private final ActivityDataRepository activityDataRepository;
    private final EmissionRecordRepository emissionRecordRepository;
    private final EmissionFactorResolver emissionFactorResolver;

    @EventListener
    @Transactional
    void onActivityDataCorrected(ActivityDataCorrectedEvent event) {
        activityDataRepository.findByIdAndTenantId(event.newActivityDataId(), event.tenantId())
            .ifPresentOrElse(
                ad -> {
                    try {
                        var date = LocalDate.of(ad.getReportingYear(), 1, 1);
                        var factor = emissionFactorResolver.resolveAt(
                            ad.getCategory(), ad.getSubCategory(), ad.getCountryCode(), date);
                        var emission = EmissionCalculator.computeEmission(
                            ad.getQuantity(), factor.factorValue());
                        String scope = deriveScopeFromCategory(ad.getCategory());
                        var domain = EmissionRecord.calculate(
                            event.tenantId(), ad.getEntityId(), ad.getId(),
                            ad.getReportingYear(), scope, null,
                            "CO2E", factor.id(), emission);
                        emissionRecordRepository.save(EmissionRecordMapper.toEntity(domain));
                        log.info("정정 후 배출량 재산출 완료: activityDataId={}", ad.getId());
                    } catch (Exception e) {
                        // 배출계수 없음 등 — 재산출 실패는 경고 로그만 (정정 자체는 이미 커밋됨)
                        log.warn("정정 후 배출량 재산출 실패: activityDataId={}, reason={}",
                            event.newActivityDataId(), e.getMessage());
                    }
                },
                () -> log.warn("재산출 대상 활동 데이터 없음: id={}", event.newActivityDataId())
            );
    }

    private String deriveScopeFromCategory(String category) {
        if (category == null) return "SCOPE1";
        if (category.endsWith("_MB")) return "SCOPE2_MB";
        if (category.startsWith("SCOPE2")) return "SCOPE2_LB";
        if (category.startsWith("SCOPE3")) return "SCOPE3";
        return "SCOPE1";
    }
}
```

- [ ] **Step 2: 빌드 확인**

```
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/internal/ActivityDataEventHandler.java
git commit -m "feat: 정정 이벤트 → 배출량 재산출 자동 트리거 (T-6B-04)"
```

---

## Task 7: 정정 통합 테스트

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/CorrectionIntegrationTest.java`

- [ ] **Step 1: 테스트 파일 작성**

```java
package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.api.*;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CorrectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired GhgService ghgService;
    @Autowired ActivityDataRepository activityDataRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000088");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000089");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000088'");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000088','CORR88','정정테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000089','00000000-0000-0000-0000-000000000088','정정법인','KR','SUBSIDIARY') " +
            "ON CONFLICT DO NOTHING");
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
    }

    // T-6B-01: 정정 후 원본 ARCHIVED, 새 레코드 생성 — 원본 quantity 불변
    @Test
    void 정정_후_원본_ARCHIVED_새_레코드_생성_원본_불변() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));

        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null,
                "계량기 재측정"));

        // 정정 레코드 확인
        assertThat(corrected.id()).isNotEqualTo(original.id());
        assertThat(corrected.quantity()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(corrected.status()).isEqualTo("DRAFT");

        // 원본 ARCHIVED 확인
        var originalEntity = activityDataRepository.findByIdAndTenantId(original.id(), TENANT_ID).orElseThrow();
        assertThat(originalEntity.getStatus()).isEqualTo("ARCHIVED");

        // 원본 quantity 불변 확인
        assertThat(originalEntity.getQuantity()).isEqualByComparingTo(new BigDecimal("1000"));
    }

    // T-6B-05: 버전 이력 조회
    @Test
    void 버전_이력_조회_원본과_정정본_모두_반환() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "재측정"));

        List<ActivityDataVersionResponse> history = ghgService.findVersionHistory(TENANT_ID, original.id());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(original.id());
        assertThat(history.get(1).id()).isEqualTo(corrected.id());
        assertThat(history.get(1).correctionOf()).isEqualTo(original.id());
    }

    // T-6B-06: diff 조회
    @Test
    void diff_조회_정정전후_수치_비교() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));
        var corrected = ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "재측정"));

        var diff = ghgService.findDiff(TENANT_ID, corrected.id());

        assertThat(diff.originalId()).isEqualTo(original.id());
        assertThat(diff.correctedId()).isEqualTo(corrected.id());
        assertThat(diff.originalQuantity()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(diff.correctedQuantity()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(diff.correctionReason()).isEqualTo("재측정");
    }

    // T-6B-02: 정정 사유 누락 → 400
    @Test
    void 정정_사유_누락_시_예외() {
        var original = ghgService.createActivityData(TENANT_ID, ENTITY_ID,
            new CreateActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1000"), "GJ", "KR", "MANUAL", null, null));

        assertThatThrownBy(() -> ghgService.correctActivityData(TENANT_ID, ACTOR_ID, original.id(),
            new CorrectActivityDataRequest(2025, "SCOPE1_FUEL", "GAS",
                new BigDecimal("1200"), "GJ", "KR", "MANUAL", null, null, "")))
            .hasMessageContaining("정정 사유");
    }
}
```

- [ ] **Step 2: 테스트 실행**

```
./gradlew test --tests "ai.claudecode.esgt2.ghg.CorrectionIntegrationTest"
```

Expected: 4 tests PASSED.

- [ ] **Step 3: 커밋**

```
git add src/test/java/ai/claudecode/esgt2/ghg/CorrectionIntegrationTest.java
git commit -m "test: 정정 워크플로우 통합 테스트 (T-6B-01~06)"
```

---

## Task 8: Formula 도메인 + 유닛 테스트 (T-6B-07) RED → GREEN

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/FormulaVersion.java`
- Create: `src/test/java/ai/claudecode/esgt2/ghg/domain/FormulaVersionDomainTest.java`

- [ ] **Step 1: FormulaVersionDomainTest 작성 (RED)**

```java
package ai.claudecode.esgt2.ghg.domain;

import ai.claudecode.esgt2.ghg.domain.formula.FormulaLoader;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FormulaVersionDomainTest {

    private static final String VALID_YAML = """
        formula:
          code: EM-TEST-001
          version: "1.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 56.0
            - inputs: { fuel: 0.0, ef: 0.056 }
              expected: 0.0
        """;

    private static final String EMPTY_TEST_CASES_YAML = """
        formula:
          code: EM-TEST-002
          version: "1.0"
          expression: "fuel * ef"
          test_cases: []
        """;

    private static final String FAILING_TEST_CASE_YAML = """
        formula:
          code: EM-TEST-003
          version: "1.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 999.0
        """;

    // T-6B-07: 유효한 YAML → 통과
    @Test
    void 유효한_YAML_test_cases_모두_통과() {
        assertThat(FormulaLoader.validate(VALID_YAML)).isTrue();
    }

    // T-6B-07: test_cases 비어있으면 활성화 차단
    @Test
    void test_cases_비어있으면_FormulaValidationException() {
        assertThatThrownBy(() -> FormulaLoader.validate(EMPTY_TEST_CASES_YAML))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases");
    }

    // T-6B-07: test_cases 결과 불일치 → 차단
    @Test
    void test_cases_결과_불일치_시_FormulaValidationException() {
        assertThatThrownBy(() -> FormulaLoader.validate(FAILING_TEST_CASE_YAML))
            .isInstanceOf(FormulaValidationException.class)
            .hasMessageContaining("test_cases 불일치");
    }
}
```

- [ ] **Step 2: 테스트 실행 (이미 FormulaLoader가 구현됐으므로 GREEN 예상)**

```
./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.FormulaVersionDomainTest"
```

Expected: 3 tests PASSED (FormulaLoader는 이미 구현됨).

- [ ] **Step 3: FormulaVersion 도메인 record 작성**

```java
package ai.claudecode.esgt2.ghg.domain;

import java.util.UUID;

/**
 * Formula 버전 도메인 객체 — 산식 코드·버전·수식·상태를 표현.
 * status: ACTIVE(활성) / INACTIVE(비활성). DELETE 없음 (P1).
 */
public record FormulaVersion(
    UUID id,
    String code,
    String version,
    String expression,
    String ghgCategory,   // 적용 GHG 카테고리 (nullable)
    String yamlContent,
    String status
) {
    public static FormulaVersion create(String code, String version, String expression,
                                        String ghgCategory, String yamlContent) {
        return new FormulaVersion(
            UUID.randomUUID(), code, version, expression, ghgCategory, yamlContent, "ACTIVE");
    }
}
```

- [ ] **Step 4: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/domain/FormulaVersion.java \
        src/test/java/ai/claudecode/esgt2/ghg/domain/FormulaVersionDomainTest.java
git commit -m "test: FormulaLoader test_cases 게이트 유닛 테스트 + FormulaVersion 도메인 (T-6B-07)"
```

---

## Task 9: Formula 인프라 + 서비스 + 컨트롤러 (T-6B-08)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/infra/FormulaVersionJpaEntity.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/infra/FormulaVersionRepository.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/FormulaVersionService.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/FormulaVersionResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/RegisterFormulaRequest.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/FormulaImpactResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultFormulaVersionService.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/FormulaController.java`

- [ ] **Step 1: FormulaVersionJpaEntity 작성**

```java
package ai.claudecode.esgt2.ghg.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "formula_versions")
@Getter
@NoArgsConstructor
public class FormulaVersionJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(length = 50)
    private String ghgCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String yamlContent;

    @Column(nullable = false, length = 20)
    private String status;   // ACTIVE / INACTIVE

    private UUID activatedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Builder
    public FormulaVersionJpaEntity(UUID id, String code, String version, String expression,
                                    String ghgCategory, String yamlContent, UUID activatedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.code = code;
        this.version = version;
        this.expression = expression;
        this.ghgCategory = ghgCategory;
        this.yamlContent = yamlContent;
        this.status = "ACTIVE";
        this.activatedBy = activatedBy;
        this.createdAt = Instant.now();
    }

    /** 비활성화 — DELETE 없음, status만 변경 (P1) */
    public void deactivate() {
        this.status = "INACTIVE";
    }
}
```

- [ ] **Step 2: FormulaVersionRepository 작성**

```java
package ai.claudecode.esgt2.ghg.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FormulaVersionRepository extends JpaRepository<FormulaVersionJpaEntity, UUID> {

    List<FormulaVersionJpaEntity> findByCode(String code);

    List<FormulaVersionJpaEntity> findByCodeAndStatus(String code, String status);

    /** 동일 code의 모든 ACTIVE 버전을 INACTIVE로 일괄 처리 (신규 등록 전 호출) */
    @Modifying
    @Query("UPDATE FormulaVersionJpaEntity f SET f.status = 'INACTIVE' WHERE f.code = :code AND f.status = 'ACTIVE'")
    void deactivateAllByCode(@Param("code") String code);
}
```

- [ ] **Step 3: DTO 파일들 작성**

`FormulaVersionResponse.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "산식 버전 응답")
public record FormulaVersionResponse(
    @Schema(description = "ID") UUID id,
    @Schema(description = "산식 코드") String code,
    @Schema(description = "버전") String version,
    @Schema(description = "수식") String expression,
    @Schema(description = "적용 GHG 카테고리") String ghgCategory,
    @Schema(description = "상태 (ACTIVE/INACTIVE)") String status,
    @Schema(description = "등록 시각") Instant createdAt
) {}
```

`RegisterFormulaRequest.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "산식 YAML 등록 요청")
public record RegisterFormulaRequest(
    @Schema(description = "산식 YAML 전체 내용 (test_cases 포함 필수)")
    @NotBlank String yamlContent
) {}
```

`FormulaImpactResponse.java`:
```java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "산식 변경 영향 조회 결과")
public record FormulaImpactResponse(
    @Schema(description = "산식 코드") String formulaCode,
    @Schema(description = "버전") String formulaVersion,
    @Schema(description = "적용 GHG 카테고리") String ghgCategory,
    @Schema(description = "영향받는 활동 데이터 건수") long affectedActivityDataCount,
    @Schema(description = "영향받는 법인 ID 목록") List<UUID> affectedEntityIds
) {}
```

- [ ] **Step 4: FormulaVersionService 인터페이스 작성**

```java
package ai.claudecode.esgt2.ghg.api;

import java.util.List;
import java.util.UUID;

public interface FormulaVersionService {

    /** 산식 YAML 등록 — test_cases 게이트 통과 시만 ACTIVE (T-6B-07, T-6B-08) */
    FormulaVersionResponse register(UUID actorId, RegisterFormulaRequest request);

    /** 동일 code의 전체 버전 이력 조회 */
    List<FormulaVersionResponse> findAll(String code);

    /** 특정 버전 비활성화 (T-6B-08) */
    FormulaVersionResponse deactivate(UUID actorId, UUID formulaVersionId);

    /** 산식 변경 영향 조회 — ghgCategory에 해당하는 활동 데이터 건수·법인 (T-6B-09) */
    FormulaImpactResponse getImpact(UUID tenantId, UUID formulaVersionId);
}
```

- [ ] **Step 5: DefaultFormulaVersionService 작성**

```java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.*;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaLoader;
import ai.claudecode.esgt2.ghg.domain.formula.FormulaValidationException;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.ghg.infra.FormulaVersionJpaEntity;
import ai.claudecode.esgt2.ghg.infra.FormulaVersionRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultFormulaVersionService implements FormulaVersionService {

    private final FormulaVersionRepository formulaVersionRepository;
    private final ActivityDataRepository activityDataRepository;

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Override
    @Transactional
    @Auditable(action = "FORMULA_VERSION_REGISTERED")
    public FormulaVersionResponse register(UUID actorId, RegisterFormulaRequest request) {
        // 1. test_cases 게이트 — 실패 시 FormulaValidationException(400) throw
        FormulaLoader.validate(request.yamlContent());

        // 2. YAML에서 메타데이터 추출
        try {
            var root = YAML_MAPPER.readTree(request.yamlContent());
            var formula = root.get("formula");
            String code = formula.get("code").asText();
            String version = formula.get("version").asText();
            String expression = formula.get("expression").asText();
            String ghgCategory = formula.has("ghg_category")
                ? formula.get("ghg_category").asText(null) : null;

            // 3. 동일 code의 기존 ACTIVE 버전 비활성화
            formulaVersionRepository.deactivateAllByCode(code);

            // 4. 새 버전 저장
            var entity = FormulaVersionJpaEntity.builder()
                .code(code).version(version).expression(expression)
                .ghgCategory(ghgCategory).yamlContent(request.yamlContent())
                .activatedBy(actorId)
                .build();
            return toResponse(formulaVersionRepository.save(entity));

        } catch (FormulaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "YAML 파싱 오류: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FormulaVersionResponse> findAll(String code) {
        return formulaVersionRepository.findByCode(code).stream()
            .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    @Auditable(action = "FORMULA_VERSION_DEACTIVATED")
    public FormulaVersionResponse deactivate(UUID actorId, UUID formulaVersionId) {
        var entity = formulaVersionRepository.findById(formulaVersionId)
            .orElseThrow(() -> new ResourceNotFoundException("formula_version", formulaVersionId));
        entity.deactivate();
        return toResponse(formulaVersionRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public FormulaImpactResponse getImpact(UUID tenantId, UUID formulaVersionId) {
        var entity = formulaVersionRepository.findById(formulaVersionId)
            .orElseThrow(() -> new ResourceNotFoundException("formula_version", formulaVersionId));
        if (entity.getGhgCategory() == null) {
            return new FormulaImpactResponse(
                entity.getCode(), entity.getVersion(), null, 0L, List.of());
        }
        var affected = activityDataRepository.findByTenantIdAndCategory(
            tenantId, entity.getGhgCategory());
        var entityIds = affected.stream()
            .map(ad -> ad.getEntityId())
            .distinct().toList();
        return new FormulaImpactResponse(
            entity.getCode(), entity.getVersion(), entity.getGhgCategory(),
            affected.size(), entityIds);
    }

    private FormulaVersionResponse toResponse(FormulaVersionJpaEntity e) {
        return new FormulaVersionResponse(
            e.getId(), e.getCode(), e.getVersion(),
            e.getExpression(), e.getGhgCategory(), e.getStatus(), e.getCreatedAt());
    }
}
```

- [ ] **Step 6: FormulaController 작성**

```java
package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ghg/formulas")
@RequiredArgsConstructor
@Tag(name = "Formula", description = "산식 버전 관리 API")
public class FormulaController {

    private final FormulaVersionService formulaVersionService;

    @Operation(summary = "산식 등록",
               description = "YAML 산식을 등록합니다. test_cases 게이트 통과 시만 ACTIVE 활성화됩니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "400", description = "test_cases 실패 또는 YAML 파싱 오류")
    @PostMapping
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<FormulaVersionResponse> register(
            Authentication authentication,
            @RequestBody @Valid RegisterFormulaRequest request) {
        var auth = (JwtAuthentication) authentication;
        var response = formulaVersionService.register(auth.getPrincipal(), request);
        return ResponseEntity.created(
            URI.create("/api/v1/ghg/formulas/" + response.id())).body(response);
    }

    @Operation(summary = "산식 버전 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<List<FormulaVersionResponse>> findAll(
            Authentication authentication,
            @RequestParam String code) {
        return ResponseEntity.ok(formulaVersionService.findAll(code));
    }

    @Operation(summary = "산식 비활성화", description = "특정 버전을 INACTIVE로 전환합니다.")
    @ApiResponse(responseCode = "200", description = "비활성화 성공")
    @PostMapping("/{formulaVersionId}/deactivate")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<FormulaVersionResponse> deactivate(
            Authentication authentication,
            @PathVariable UUID formulaVersionId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            formulaVersionService.deactivate(auth.getPrincipal(), formulaVersionId));
    }

    @Operation(summary = "산식 변경 영향 조회",
               description = "산식 버전의 ghgCategory에 해당하는 활동 데이터 건수와 법인 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/{formulaVersionId}/impact")
    @PreAuthorize("hasAnyRole('ESG_MANAGER', 'ESG_VIEWER')")
    public ResponseEntity<FormulaImpactResponse> getImpact(
            Authentication authentication,
            @PathVariable UUID formulaVersionId) {
        var auth = (JwtAuthentication) authentication;
        return ResponseEntity.ok(
            formulaVersionService.getImpact(auth.getTenantId(), formulaVersionId));
    }
}
```

- [ ] **Step 7: 빌드 확인**

```
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/ghg/
git commit -m "feat: Formula 버전 관리 — 등록·비활성화·영향 조회 (T-6B-08, T-6B-09)"
```

---

## Task 10: Formula 통합 테스트 + ModularityTest

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/FormulaVersionIntegrationTest.java`

- [ ] **Step 1: 통합 테스트 작성**

```java
package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.ghg.api.*;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class FormulaVersionIntegrationTest extends AbstractIntegrationTest {

    @Autowired FormulaVersionService formulaVersionService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000090");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String VALID_YAML = """
        formula:
          code: EM-SCOPE1-FUEL
          version: "2.0"
          ghg_category: SCOPE1_FUEL
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 56.0
        """;

    private static final String FAILING_YAML = """
        formula:
          code: EM-SCOPE1-FUEL
          version: "3.0"
          expression: "fuel * ef"
          test_cases:
            - inputs: { fuel: 1000.0, ef: 0.056 }
              expected: 9999.0
        """;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM formula_versions WHERE code LIKE 'EM-SCOPE1%'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000090'");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000090','FORM90','포뮬라테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000091','00000000-0000-0000-0000-000000000090','포뮬라법인','KR','SUBSIDIARY') " +
            "ON CONFLICT DO NOTHING");
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
    }

    // T-6B-07, T-6B-08: test_cases 통과 → 등록 성공
    @Test
    void test_cases_통과_산식_등록_성공() {
        var response = formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(VALID_YAML));

        assertThat(response.code()).isEqualTo("EM-SCOPE1-FUEL");
        assertThat(response.version()).isEqualTo("2.0");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.ghgCategory()).isEqualTo("SCOPE1_FUEL");
    }

    // T-6B-07: test_cases 실패 → 활성화 차단
    @Test
    void test_cases_실패_산식_등록_차단() {
        assertThatThrownBy(() ->
            formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(FAILING_YAML)))
            .hasMessageContaining("test_cases");
    }

    // T-6B-08: 신규 버전 등록 시 이전 ACTIVE 버전 자동 INACTIVE
    @Test
    void 신규_버전_등록_시_이전_ACTIVE_비활성화() {
        formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(VALID_YAML));

        String updatedYaml = VALID_YAML.replace("version: \"2.0\"", "version: \"2.1\"");
        formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(updatedYaml));

        var versions = formulaVersionService.findAll("EM-SCOPE1-FUEL");
        assertThat(versions).hasSize(2);
        assertThat(versions.stream().filter(v -> "ACTIVE".equals(v.status())).count()).isEqualTo(1);
        assertThat(versions.stream().filter(v -> "INACTIVE".equals(v.status())).count()).isEqualTo(1);
    }

    // T-6B-09: 영향 조회 — ghgCategory에 해당하는 활동 데이터 반환
    @Test
    void 산식_변경_영향_활동데이터_조회() {
        var formula = formulaVersionService.register(ACTOR_ID, new RegisterFormulaRequest(VALID_YAML));

        // 같은 카테고리 활동 데이터 삽입
        jdbcTemplate.execute(
            "INSERT INTO activity_data (id, tenant_id, entity_id, reporting_year, category, " +
            "sub_category, quantity, unit, country_code, data_source, data_quality, status, created_at, updated_at) " +
            "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000090', " +
            "'00000000-0000-0000-0000-000000000091', 2025, 'SCOPE1_FUEL', 'GAS', " +
            "1000.0, 'GJ', 'KR', 'MANUAL', 'AVERAGE_DATA', 'DRAFT', NOW(), NOW())");

        var impact = formulaVersionService.getImpact(TENANT_ID, formula.id());

        assertThat(impact.formulaCode()).isEqualTo("EM-SCOPE1-FUEL");
        assertThat(impact.ghgCategory()).isEqualTo("SCOPE1_FUEL");
        assertThat(impact.affectedActivityDataCount()).isEqualTo(1L);
        assertThat(impact.affectedEntityIds()).containsExactly(ENTITY_ID);
    }
}
```

- [ ] **Step 2: 통합 테스트 실행**

```
./gradlew test --tests "ai.claudecode.esgt2.ghg.FormulaVersionIntegrationTest"
```

Expected: 4 tests PASSED.

- [ ] **Step 3: ModularityTest 실행**

```
./gradlew test --tests "*ModularityTest"
```

Expected: PASSED.

- [ ] **Step 4: 전체 테스트 실행**

```
./gradlew test 2>&1 | tail -5
```

Expected: 이전과 동일하게 `ActuatorEndpointTest` 1건 제외 전체 통과.

- [ ] **Step 5: task.md 업데이트 — T-6B-01~09 DONE**

`docs/task.md`에서 T-6B-01~09를 TODO → DONE으로 변경한다:

```
| T-6B-01 | `test:` 활동 데이터 정정 → 새 버전 INSERT (원본 불변 확인) | DONE | ...
| T-6B-02 | `test:` 정정 사유 코드 누락 → 예외 | DONE | ...
| T-6B-03 | `feat:` POST /api/v1/ghg/activity-data/{id}/correct @Auditable | DONE | ...
| T-6B-04 | `feat:` 정정 이벤트 → 연결 배출량 재산출 자동 트리거 | DONE | ...
| T-6B-05 | `feat:` GET /api/v1/ghg/activity-data/{id}/versions | DONE | ...
| T-6B-06 | `feat:` 정정 전·후 수치 비교 API (/diff) | DONE | ...
| T-6B-07 | `test:` Formula YAML test_cases 실패 → 활성화 차단 | DONE | ...
| T-6B-08 | `feat:` Formula 버전 관리 (활성/비활성) | DONE | ...
| T-6B-09 | `feat:` 산식 변경 → 영향받는 배출량 목록 조회 API | DONE | ...
```

- [ ] **Step 6: 최종 커밋**

```
git add src/test/java/ai/claudecode/esgt2/ghg/FormulaVersionIntegrationTest.java \
        docs/task.md
git commit -m "test: Formula 버전 통합 테스트 + task.md T-6B-01~09 DONE (T-6B-07, T-6B-08, T-6B-09)"
```

---

## 자기 검토 (Self-Review)

### 1. 스펙 커버리지

| 태스크 | 구현 위치 |
|---|---|
| T-6B-01 정정 → INSERT, 원본 불변 | Task 3 `ActivityData.correct()` + Task 4 JPA + Task 7 통합 테스트 |
| T-6B-02 정정 사유 누락 → 예외 | Task 3 `CorrectActivityDataCommand` compact constructor |
| T-6B-03 POST correct @Auditable | Task 5 `DefaultGhgService` + GhgController |
| T-6B-04 재산출 자동 트리거 | Task 6 `ActivityDataEventHandler` @EventListener |
| T-6B-05 버전 이력 조회 | Task 5 `findVersionHistory()` + Task 5 endpoint |
| T-6B-06 diff API | Task 5 `findDiff()` + Task 5 endpoint |
| T-6B-07 test_cases 게이트 | Task 8 `FormulaVersionDomainTest` + 기존 `FormulaLoader` |
| T-6B-08 Formula 버전 관리 | Task 9 `DefaultFormulaVersionService` + `FormulaController` |
| T-6B-09 영향 조회 | Task 9 `getImpact()` + `FormulaController` |

### 2. Placeholder 없음 ✅

### 3. 타입 일관성 확인

- `ActivityDataCorrectedEvent` — Task 5에서 생성, Task 6에서 수신 ✅
- `CorrectActivityDataCommand` — Task 3에서 정의, Task 5에서 사용 ✅
- `ActivityDataVersionResponse` / `ActivityDataDiffResponse` — Task 5에서 정의, GhgService에서 반환 ✅
- `FormulaVersionResponse` / `RegisterFormulaRequest` / `FormulaImpactResponse` — Task 9에서 정의, FormulaVersionService에서 사용 ✅
- `findByTenantIdAndCategory` — Task 4에서 Repository에 추가, Task 9에서 사용 ✅
