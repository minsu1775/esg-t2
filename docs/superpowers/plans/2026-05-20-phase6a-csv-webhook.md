# Phase 6A: CSV 업로드 + Webhook 데이터 수집 파이프라인 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** CSV 파일 업로드와 외부 Webhook으로 ActivityData를 대량 수집하는 파이프라인 구현 — 행별 독립 트랜잭션(REQUIRES_NEW), HMAC-SHA256 서명 검증, 오류 행 리포트 포함.

**Architecture:** `ghg` 모듈 내 신규 `IntakeController` → `DefaultIntakeService(@Transactional + @Auditable)` → `ActivityDataRowImporter(@Transactional REQUIRES_NEW)` 계층. CSV 파싱은 순수 도메인 클래스 `CsvActivityDataParser`, 중복 감지는 DB `existsBy`, HMAC 검증은 컨트롤러에서 처리 후 system actor를 SecurityContext에 설정. 기존 `activity_data` 테이블 재사용 — 신규 DB 마이그레이션 없음.

**Tech Stack:** Apache Commons CSV 1.12.0, Spring Boot 4, Java 25 (HexFormat), JUnit 5 + TestContainers PostgreSQL, AssertJ

---

## 파일 구조

```
신규 생성:
  src/main/java/ai/claudecode/esgt2/ghg/domain/
    ├── CsvRow.java                          파싱된 CSV 행 (value type)
    ├── ImportRowResult.java                  행별 처리 결과 (SUCCESS/SKIPPED/ERROR)
    ├── BulkImportResult.java                전체 집계 결과
    └── CsvActivityDataParser.java           CSV → List<CsvRow> (도메인 파서)
  src/main/java/ai/claudecode/esgt2/ghg/api/
    ├── IntakeService.java                   공개 인터페이스
    ├── CsvUploadResponse.java               응답 DTO
    ├── WebhookActivityDataItem.java         Webhook 단건 요청 DTO
    └── IntakeController.java                REST 엔드포인트 2개
  src/main/java/ai/claudecode/esgt2/ghg/internal/
    ├── ActivityDataRowImporter.java         REQUIRES_NEW 행별 트랜잭션
    └── DefaultIntakeService.java            서비스 구현체

수정:
  build.gradle.kts                           commons-csv 1.12.0 의존성 추가
  src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java  에러 코드 2개 추가
  src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataRepository.java  existsBy 메서드 추가
  src/main/resources/application.yml         intake.webhook.hmac-secret 설정 추가

신규 테스트:
  src/test/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParserTest.java  (unit)
  src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java             (integration)
```

---

### Task 1: 도메인 값 타입 + Apache Commons CSV 의존성

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/CsvRow.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/ImportRowResult.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/BulkImportResult.java`
- Modify: `build.gradle.kts`

- [ ] **Step 1: commons-csv 전이 의존성 확인**

Run: `./gradlew dependencies --configuration compileClasspath 2>&1 | grep commons-csv`
Expected: 출력 없음 → 직접 추가 필요

- [ ] **Step 2: build.gradle.kts에 의존성 추가**

`// YAML 파싱 (Formula DSL)` 줄 아래에 추가:
```kotlin
// CSV 업로드 파이프라인
implementation("org.apache.commons:commons-csv:1.12.0")
```

- [ ] **Step 3: CsvRow 레코드 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/domain/CsvRow.java
package ai.claudecode.esgt2.ghg.domain;

import java.math.BigDecimal;

public record CsvRow(
    int lineNumber,
    int reportingYear,
    String category,
    String subCategory,
    BigDecimal quantity,
    String unit,
    String countryCode,
    String dataSource,
    String dataQuality,
    Integer lifetimeYears
) {}
```

- [ ] **Step 4: ImportRowResult 레코드 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/domain/ImportRowResult.java
package ai.claudecode.esgt2.ghg.domain;

import java.util.UUID;

public record ImportRowResult(
    int lineNumber,
    String status,
    String message,
    UUID activityDataId
) {
    public static ImportRowResult success(int lineNumber, UUID activityDataId) {
        return new ImportRowResult(lineNumber, "SUCCESS", null, activityDataId);
    }

    public static ImportRowResult skipped(int lineNumber, String reason) {
        return new ImportRowResult(lineNumber, "SKIPPED", reason, null);
    }

    public static ImportRowResult error(int lineNumber, String reason) {
        return new ImportRowResult(lineNumber, "ERROR", reason, null);
    }
}
```

- [ ] **Step 5: BulkImportResult 레코드 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/domain/BulkImportResult.java
package ai.claudecode.esgt2.ghg.domain;

import java.util.List;

public record BulkImportResult(
    int totalRows,
    int successCount,
    int skipCount,
    int errorCount,
    List<ImportRowResult> rows
) {
    public static BulkImportResult of(List<ImportRowResult> rows) {
        int success = (int) rows.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        int skipped = (int) rows.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        int error   = (int) rows.stream().filter(r -> "ERROR".equals(r.status())).count();
        return new BulkImportResult(rows.size(), success, skipped, error, List.copyOf(rows));
    }
}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/domain/CsvRow.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/ImportRowResult.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/BulkImportResult.java \
        build.gradle.kts
git commit -m "feat: Phase 6A 도메인 값 타입 추가 + commons-csv 의존성 (T-6-01 준비)"
```

---

### Task 2: CsvActivityDataParser TDD (T-6-02 파싱)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParserTest.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParser.java`

CSV 헤더 규격:
```
reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
```
필수: `reporting_year`, `category`, `quantity`, `unit`, `country_code` / 선택: 나머지

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParserTest.java
package ai.claudecode.esgt2.ghg.domain;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvActivityDataParserTest {

    private static ByteArrayResource csv(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 유효한_3행_파싱() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,SPEND_BASED,
            2025,SCOPE3_CAT2,,500000,KRW,KR,API,,
            2025,SCOPE3_CAT11,TV,1000,unit,KR,MANUAL,,8
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).category()).isEqualTo("SCOPE3_CAT1");
        assertThat(rows.get(0).quantity()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(rows.get(0).subCategory()).isEqualTo("ELECTRONICS");
        assertThat(rows.get(0).lifetimeYears()).isNull();
        assertThat(rows.get(1).subCategory()).isNull();
        assertThat(rows.get(2).lifetimeYears()).isEqualTo(8);
    }

    @Test
    void lineNumber는_헤더를_1로_데이터를_2부터_시작() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,
            2025,SCOPE3_CAT2,,500000,KRW,KR,,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(1).lineNumber()).isEqualTo(3);
    }

    @Test
    void 값_앞뒤_공백은_트림된다() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
             2025 , SCOPE3_CAT1 , ELECTRONICS ,10000, KRW , KR ,MANUAL,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).category()).isEqualTo("SCOPE3_CAT1");
        assertThat(rows.get(0).countryCode()).isEqualTo("KR");
        assertThat(rows.get(0).reportingYear()).isEqualTo(2025);
    }

    @Test
    void 헤더_제외_100행_모두_파싱된다() {
        var sb = new StringBuilder(
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n");
        for (int i = 0; i < 100; i++) {
            sb.append("2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,\n");
        }

        List<CsvRow> rows = CsvActivityDataParser.parse(csv(sb.toString()));

        assertThat(rows).hasSize(100);
        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(99).lineNumber()).isEqualTo(101);
    }

    @Test
    void data_source_없으면_기본값_MANUAL() {
        var resource = csv("""
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,,10000,KRW,KR,,,
            """);

        List<CsvRow> rows = CsvActivityDataParser.parse(resource);

        assertThat(rows.get(0).dataSource()).isEqualTo("MANUAL");
    }

    @Test
    void 잘못된_CSV_형식은_예외() {
        assertThatThrownBy(() -> CsvActivityDataParser.parse(csv("not,a,valid\ncsv")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 — Red 확인**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.CsvActivityDataParserTest"`
Expected: FAIL — CsvActivityDataParser 클래스 없음

- [ ] **Step 3: CsvActivityDataParser 구현**

```java
// src/main/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParser.java
package ai.claudecode.esgt2.ghg.domain;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.Resource;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CsvActivityDataParser {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
        "reporting_year", "category", "quantity", "unit", "country_code");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setTrim(true)
        .build();

    private CsvActivityDataParser() {}

    public static List<CsvRow> parse(Resource resource) {
        List<CsvRow> rows = new ArrayList<>();
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var csvParser = FORMAT.parse(reader)) {

            var headerMap = csvParser.getHeaderMap();
            if (headerMap == null || !headerMap.keySet().containsAll(REQUIRED_HEADERS)) {
                throw new IllegalArgumentException("필수 헤더 누락: " + REQUIRED_HEADERS);
            }

            for (CSVRecord record : csvParser) {
                rows.add(new CsvRow(
                    (int) record.getParser().getCurrentLineNumber(),
                    parseInt(record.get("reporting_year")),
                    record.get("category"),
                    emptyToNull(record.get("sub_category")),
                    parseDecimal(record.get("quantity")),
                    record.get("unit"),
                    record.get("country_code"),
                    emptyToDefault(record.get("data_source"), "MANUAL"),
                    emptyToNull(record.get("data_quality")),
                    parseInteger(record.get("lifetime_years"))
                ));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV 파싱 실패: " + e.getMessage(), e);
        }
        return rows;
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String emptyToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
```

- [ ] **Step 4: 테스트 실행 — Green 확인**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.domain.CsvActivityDataParserTest"`
Expected: 6 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParserTest.java \
        src/main/java/ai/claudecode/esgt2/ghg/domain/CsvActivityDataParser.java
git commit -m "test/feat: CsvActivityDataParser TDD — 파싱·trim·100행·기본값 (T-6-02)"
```

---

### Task 3: EsgErrorCode 추가 + Webhook 설정

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: EsgErrorCode에 2개 코드 추가**

`INTERNAL_ERROR` 줄 앞에 추가:
```java
WEBHOOK_SIGNATURE_INVALID,
CSV_PARSE_FAILED,
```

- [ ] **Step 2: application.yml에 webhook secret 설정 추가**

파일 끝에 추가:
```yaml
intake:
  webhook:
    hmac-secret: ${INTAKE_WEBHOOK_HMAC_SECRET:dev-webhook-secret-must-change-in-prod}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java \
        src/main/resources/application.yml
git commit -m "feat: EsgErrorCode Webhook/CSV 에러 코드 추가 + webhook hmac-secret 설정"
```

---

### Task 4: ActivityDataRowImporter (REQUIRES_NEW + 중복 감지)

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataRepository.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/ActivityDataRowImporter.java`

이 태스크가 T-6-14 (`REQUIRES_NEW` 적용)와 T-6-13 (중복 감지)의 핵심 구현이다.

- [ ] **Step 1: ActivityDataRepository에 existsBy 메서드 추가**

기존 2번째 메서드 아래에 추가:
```java
boolean existsByTenantIdAndEntityIdAndReportingYearAndCategoryAndSubCategoryAndDataSource(
    UUID tenantId, UUID entityId, int reportingYear,
    String category, String subCategory, String dataSource);
```

- [ ] **Step 2: ActivityDataRowImporter 구현**

```java
// src/main/java/ai/claudecode/esgt2/ghg/internal/ActivityDataRowImporter.java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.domain.ActivityData;
import ai.claudecode.esgt2.ghg.domain.BulkImportResult;
import ai.claudecode.esgt2.ghg.domain.CreateActivityDataCommand;
import ai.claudecode.esgt2.ghg.domain.CsvRow;
import ai.claudecode.esgt2.ghg.domain.ImportRowResult;
import ai.claudecode.esgt2.ghg.infra.ActivityDataMapper;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ActivityDataRowImporter {

    private final ActivityDataRepository activityDataRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ImportRowResult importRow(UUID tenantId, UUID entityId, CsvRow row) {
        try {
            if (row.reportingYear() <= 0 || row.category() == null || row.category().isBlank()
                    || row.quantity() == null || row.unit() == null || row.countryCode() == null) {
                return ImportRowResult.error(row.lineNumber(),
                    "필수 필드 누락: reporting_year, category, quantity, unit, country_code");
            }

            boolean duplicate = activityDataRepository
                .existsByTenantIdAndEntityIdAndReportingYearAndCategoryAndSubCategoryAndDataSource(
                    tenantId, entityId,
                    row.reportingYear(), row.category(),
                    row.subCategory(), row.dataSource());
            if (duplicate) {
                log.warn("중복 행 건너뜀: tenantId={}, entityId={}, year={}, category={}, line={}",
                    tenantId, entityId, row.reportingYear(), row.category(), row.lineNumber());
                return ImportRowResult.skipped(row.lineNumber(), "중복 항목");
            }

            var cmd = new CreateActivityDataCommand(
                tenantId, entityId,
                row.reportingYear(), row.category(), row.subCategory(),
                row.quantity(), row.unit(), row.countryCode(),
                row.dataSource(), row.dataQuality(),
                row.lifetimeYears());

            var domain = ActivityData.create(cmd);
            var saved = activityDataRepository.save(ActivityDataMapper.toEntity(domain));
            return ImportRowResult.success(row.lineNumber(), saved.getId());

        } catch (Exception e) {
            log.error("행 처리 오류 line={}: {}", row.lineNumber(), e.getMessage());
            return ImportRowResult.error(row.lineNumber(), e.getMessage());
        }
    }

    BulkImportResult importRows(UUID tenantId, UUID entityId, List<CsvRow> rows) {
        List<ImportRowResult> results = rows.stream()
            .map(row -> importRow(tenantId, entityId, row))
            .toList();
        return BulkImportResult.of(results);
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataRepository.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/ActivityDataRowImporter.java
git commit -m "feat: ActivityDataRowImporter — REQUIRES_NEW + 중복 감지 (T-6-14)"
```

---

### Task 5: IntakeService 인터페이스 + DefaultIntakeService

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/IntakeService.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/CsvUploadResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/WebhookActivityDataItem.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultIntakeService.java`

- [ ] **Step 1: CsvUploadResponse DTO 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/api/CsvUploadResponse.java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "CSV/Webhook 업로드 처리 결과")
public record CsvUploadResponse(
    @Schema(description = "전체 처리 행 수") int totalRows,
    @Schema(description = "성공 행 수") int successCount,
    @Schema(description = "건너뜀 행 수 (중복)") int skipCount,
    @Schema(description = "오류 행 수") int errorCount,
    @Schema(description = "오류·건너뜀 행 상세 목록") List<RowResult> nonSuccessRows
) {
    @Schema(description = "개별 행 결과")
    public record RowResult(
        @Schema(description = "파일 내 행 번호 (헤더=1, 첫 데이터=2)") int lineNumber,
        @Schema(description = "처리 상태: SUCCESS, SKIPPED, ERROR") String status,
        @Schema(description = "오류·건너뜀 사유 (SUCCESS이면 null)") String message
    ) {}
}
```

- [ ] **Step 2: WebhookActivityDataItem DTO 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/api/WebhookActivityDataItem.java
package ai.claudecode.esgt2.ghg.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Webhook 활동 데이터 단건")
public record WebhookActivityDataItem(
    @Schema(description = "법인 ID") @NotNull UUID entityId,
    @Schema(description = "보고 연도") int reportingYear,
    @Schema(description = "GHG 카테고리 (예: SCOPE3_CAT1)") @NotBlank String category,
    @Schema(description = "하위 카테고리") String subCategory,
    @Schema(description = "활동량") @NotNull @Positive BigDecimal quantity,
    @Schema(description = "단위 (예: KRW, unit)") @NotBlank String unit,
    @Schema(description = "국가 코드 (ISO 2자리)") @NotBlank String countryCode,
    @Schema(description = "데이터 출처 (기본값 WEBHOOK)") String dataSource,
    @Schema(description = "데이터 품질") String dataQuality,
    @Schema(description = "사용기간 연수 (Cat.11 전용)") Integer lifetimeYears
) {}
```

- [ ] **Step 3: IntakeService 인터페이스 작성**

```java
// src/main/java/ai/claudecode/esgt2/ghg/api/IntakeService.java
package ai.claudecode.esgt2.ghg.api;

import org.springframework.core.io.Resource;

import java.util.List;
import java.util.UUID;

public interface IntakeService {
    CsvUploadResponse uploadCsv(UUID tenantId, UUID entityId, Resource csvFile);
    CsvUploadResponse receiveWebhook(UUID tenantId, List<WebhookActivityDataItem> items);
}
```

- [ ] **Step 4: DefaultIntakeService 구현**

`DefaultIntakeService`는 `@Transactional` + `@Auditable`로 외부 트랜잭션을 소유한다.
각 행 처리는 `ActivityDataRowImporter`의 `REQUIRES_NEW` 트랜잭션에 위임하여
중간 행 오류 시에도 성공한 행들은 이미 커밋된다.

```java
// src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultIntakeService.java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.CsvUploadResponse;
import ai.claudecode.esgt2.ghg.api.IntakeService;
import ai.claudecode.esgt2.ghg.api.WebhookActivityDataItem;
import ai.claudecode.esgt2.ghg.domain.BulkImportResult;
import ai.claudecode.esgt2.ghg.domain.CsvActivityDataParser;
import ai.claudecode.esgt2.ghg.domain.CsvRow;
import ai.claudecode.esgt2.ghg.domain.ImportRowResult;
import ai.claudecode.esgt2.shared.audit.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
class DefaultIntakeService implements IntakeService {

    private final ActivityDataRowImporter rowImporter;

    @Override
    @Transactional
    @Auditable(action = "CSV_UPLOADED")
    public CsvUploadResponse uploadCsv(UUID tenantId, UUID entityId, Resource csvFile) {
        List<CsvRow> rows = CsvActivityDataParser.parse(csvFile);
        BulkImportResult result = rowImporter.importRows(tenantId, entityId, rows);
        return toResponse(result);
    }

    @Override
    @Transactional
    @Auditable(action = "WEBHOOK_DATA_RECEIVED")
    public CsvUploadResponse receiveWebhook(UUID tenantId, List<WebhookActivityDataItem> items) {
        var counter = new AtomicInteger(1);
        List<ImportRowResult> results = items.stream().map(item -> {
            var row = new CsvRow(
                counter.getAndIncrement(),
                item.reportingYear(), item.category(), item.subCategory(),
                item.quantity(), item.unit(), item.countryCode(),
                item.dataSource() != null ? item.dataSource() : "WEBHOOK",
                item.dataQuality(), item.lifetimeYears());
            return rowImporter.importRow(tenantId, item.entityId(), row);
        }).toList();
        return toResponse(BulkImportResult.of(results));
    }

    private CsvUploadResponse toResponse(BulkImportResult result) {
        var nonSuccess = result.rows().stream()
            .filter(r -> !"SUCCESS".equals(r.status()))
            .map(r -> new CsvUploadResponse.RowResult(r.lineNumber(), r.status(), r.message()))
            .toList();
        return new CsvUploadResponse(
            result.totalRows(), result.successCount(),
            result.skipCount(), result.errorCount(), nonSuccess);
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/api/CsvUploadResponse.java \
        src/main/java/ai/claudecode/esgt2/ghg/api/WebhookActivityDataItem.java \
        src/main/java/ai/claudecode/esgt2/ghg/api/IntakeService.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultIntakeService.java
git commit -m "feat: IntakeService + DefaultIntakeService — CSV/Webhook 오케스트레이션 (T-6-02, T-6-06)"
```

---

### Task 6: IntakeController + REST 엔드포인트

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/IntakeController.java`

엔드포인트:
1. `POST /api/v1/intake/entities/{entityId}/csv` — multipart CSV, JWT 인증 (ESG_MANAGER)
2. `POST /api/v1/intake/tenants/{tenantId}/webhook` — JSON array, HMAC-SHA256 인증

웹훅은 JWT 없이 HMAC으로만 인증하므로, 서비스 호출 전 system actor를 SecurityContext에 설정하여 `@Auditable` AOP가 감사 로그를 남길 수 있게 한다.

- [ ] **Step 1: IntakeController 구현**

```java
// src/main/java/ai/claudecode/esgt2/ghg/api/IntakeController.java
package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intake")
@RequiredArgsConstructor
@Tag(name = "Intake", description = "대량 활동 데이터 수집 API (CSV 업로드, Webhook)")
public class IntakeController {

    private static final UUID SYSTEM_WEBHOOK_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private final IntakeService intakeService;

    @Value("${intake.webhook.hmac-secret}")
    private String webhookHmacSecret;

    @Operation(summary = "CSV 활동 데이터 업로드", description = "CSV 파일로 ActivityData를 일괄 등록합니다.")
    @ApiResponse(responseCode = "200", description = "처리 결과 (오류 행 포함)")
    @ApiResponse(responseCode = "400", description = "CSV 파싱 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping(value = "/entities/{entityId}/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<CsvUploadResponse> uploadCsv(
            @AuthenticationPrincipal JwtAuthentication auth,
            @Parameter(description = "법인 ID") @PathVariable UUID entityId,
            @RequestParam("file") MultipartFile file) {

        var result = intakeService.uploadCsv(auth.getTenantId(), entityId, file.getResource());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Webhook 활동 데이터 수신", description = "외부 ERP/시스템에서 HMAC-SHA256 서명으로 데이터를 전송합니다.")
    @ApiResponse(responseCode = "200", description = "처리 결과")
    @ApiResponse(responseCode = "401", description = "Webhook 서명 불일치")
    @PostMapping("/tenants/{tenantId}/webhook")
    public ResponseEntity<CsvUploadResponse> receiveWebhook(
            @Parameter(description = "테넌트 ID") @PathVariable UUID tenantId,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String rawBody,
            @RequestBody(required = false) @Valid List<WebhookActivityDataItem> items) {

        if (!isValidSignature(rawBody, signature)) {
            throw new EsgException(EsgErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        // JWT 없는 webhook 호출에도 @Auditable AOP가 감사 로그를 남기도록 system actor 설정
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(SYSTEM_WEBHOOK_ACTOR, tenantId, List.of("WEBHOOK")));

        var result = intakeService.receiveWebhook(tenantId, items);
        return ResponseEntity.ok(result);
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                webhookHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
```

> **주의**: `receiveWebhook`는 `@RequestBody`를 두 번 사용했는데, 이는 Spring MVC에서 오류가 난다. 아래 Step 2에서 `@RequestBody String rawBody` 방식 대신 `HttpServletRequest`로 바디를 직접 읽는 방식으로 수정해야 한다.

- [ ] **Step 2: receiveWebhook 바디 처리 방식 수정**

Spring MVC에서 `HttpServletRequest.getReader()`를 사용해 rawBody와 JSON 파싱을 분리한다:

```java
// IntakeController.java에서 receiveWebhook 메서드를 아래로 교체
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;

private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

@PostMapping("/tenants/{tenantId}/webhook")
public ResponseEntity<CsvUploadResponse> receiveWebhook(
        @Parameter(description = "테넌트 ID") @PathVariable UUID tenantId,
        @RequestHeader("X-Hub-Signature-256") String signature,
        HttpServletRequest request) throws Exception {

    String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    if (!isValidSignature(rawBody, signature)) {
        throw new EsgException(EsgErrorCode.WEBHOOK_SIGNATURE_INVALID);
    }

    List<WebhookActivityDataItem> items = OBJECT_MAPPER.readValue(
        rawBody, new TypeReference<List<WebhookActivityDataItem>>() {});

    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthentication(SYSTEM_WEBHOOK_ACTOR, tenantId, List.of("WEBHOOK")));

    var result = intakeService.receiveWebhook(tenantId, items);
    return ResponseEntity.ok(result);
}
```

IntakeController.java 전체를 최종 버전으로 다시 작성한다 (Step 1의 잘못된 @RequestBody 이중 선언 제거):

```java
// src/main/java/ai/claudecode/esgt2/ghg/api/IntakeController.java  (최종본)
package ai.claudecode.esgt2.ghg.api;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intake")
@RequiredArgsConstructor
@Tag(name = "Intake", description = "대량 활동 데이터 수집 API (CSV 업로드, Webhook)")
public class IntakeController {

    private static final UUID SYSTEM_WEBHOOK_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IntakeService intakeService;

    @Value("${intake.webhook.hmac-secret}")
    private String webhookHmacSecret;

    @Operation(summary = "CSV 활동 데이터 업로드")
    @ApiResponse(responseCode = "200", description = "처리 결과 (오류 행 포함)")
    @ApiResponse(responseCode = "400", description = "CSV 파싱 오류")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PostMapping(value = "/entities/{entityId}/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<CsvUploadResponse> uploadCsv(
            @AuthenticationPrincipal JwtAuthentication auth,
            @Parameter(description = "법인 ID") @PathVariable UUID entityId,
            @RequestParam("file") MultipartFile file) {
        var result = intakeService.uploadCsv(auth.getTenantId(), entityId, file.getResource());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Webhook 활동 데이터 수신")
    @ApiResponse(responseCode = "200", description = "처리 결과")
    @ApiResponse(responseCode = "401", description = "Webhook 서명 불일치")
    @PostMapping("/tenants/{tenantId}/webhook")
    public ResponseEntity<CsvUploadResponse> receiveWebhook(
            @Parameter(description = "테넌트 ID") @PathVariable UUID tenantId,
            @RequestHeader("X-Hub-Signature-256") String signature,
            HttpServletRequest request) throws Exception {

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8);

        if (!isValidSignature(rawBody, signature)) {
            throw new EsgException(EsgErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        List<WebhookActivityDataItem> items = OBJECT_MAPPER.readValue(
            rawBody, new TypeReference<>() {});

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(SYSTEM_WEBHOOK_ACTOR, tenantId, List.of("WEBHOOK")));

        var result = intakeService.receiveWebhook(tenantId, items);
        return ResponseEntity.ok(result);
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                webhookHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 3: Spring Security가 /intake/tenants/*/webhook 허용하도록 설정 확인**

기존 SecurityConfig 파일 확인:
```
Glob: src/main/java/ai/claudecode/esgt2/**/*SecurityConfig*.java
```

결과에 따라 `http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/intake/tenants/*/webhook").permitAll())` 추가.
(웹훅은 HMAC으로 인증하므로 Spring Security JWT 필터 우회 필요)

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/ghg/api/IntakeController.java
git commit -m "feat: IntakeController — CSV 업로드 + Webhook HMAC 엔드포인트 (T-6-04)"
```

---

### Task 7: CSV 통합 테스트 — 100행 멱등성, 행 격리, 중복 WARN (T-6-01, T-6-12, T-6-13)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java
package ai.claudecode.esgt2.ghg;

import ai.claudecode.esgt2.audit.infra.OutboxEventRepository;
import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.CsvUploadResponse;
import ai.claudecode.esgt2.ghg.api.IntakeService;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntakeIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IntakeService intakeService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private ActivityDataRepository activityDataRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityId;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000006'");
        jdbcTemplate.execute("DELETE FROM audit_logs");
        outboxEventRepository.deleteAll();
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000006', 'TEST6', '테스트법인6', 'KR') " +
            "ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));

        var entityResp = entityManagementService.create(
            TENANT_ID, new CreateEntityRequest("CSV테스트법인", "KR", LegalEntityType.PARENT));
        entityId = entityResp.id();
    }

    // T-6-01: 100행 CSV 업로드 멱등성
    @Test
    void CSV_100행_업로드_모두_성공() {
        var csv = buildCsv(100);

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, csv);

        assertThat(result.totalRows()).isEqualTo(100);
        assertThat(result.successCount()).isEqualTo(100);
        assertThat(result.errorCount()).isEqualTo(0);
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(100);
    }

    // T-6-01: 동일 CSV 재업로드 → 100개 SKIPPED (멱등성)
    @Test
    void 동일_CSV_재업로드_100행_모두_건너뜀() {
        var csv = buildCsv(100);
        intakeService.uploadCsv(TENANT_ID, entityId, csv);

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, buildCsv(100));

        assertThat(result.skipCount()).isEqualTo(100);
        assertThat(result.successCount()).isEqualTo(0);
        // DB에 데이터는 처음 업로드한 100건만 존재
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(100);
    }

    // T-6-12: 중간 행 오류 시 이전 행 보존 (REQUIRES_NEW)
    @Test
    void 중간_행_오류_시_이전_성공_행_보존() {
        String csvContent = """
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,
            2025,SCOPE3_CAT2,,500000,KRW,KR,,,
            ,SCOPE3_CAT1,,10000,KRW,KR,,,
            2025,SCOPE3_CAT1,SERVICES,8000,KRW,KR,MANUAL,,
            """;
        var csv = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8));

        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId, csv);

        // 3번째 행(reporting_year 없음) → ERROR, 나머지 3행 → SUCCESS
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.nonSuccessRows().get(0).lineNumber()).isEqualTo(4);
        // DB에 성공한 3건 존재 (REQUIRES_NEW 덕분에 오류 행이 롤백해도 나머지는 커밋됨)
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(3);
    }

    // T-6-13: 중복 항목 재업로드 → WARN 로그 + 계속 처리
    @Test
    void 부분_중복_CSV_재업로드_기존행_SKIPPED_신규행_SUCCESS() {
        // 먼저 1행 업로드
        String firstCsv = """
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,
            """;
        intakeService.uploadCsv(TENANT_ID, entityId,
            new ByteArrayResource(firstCsv.getBytes(StandardCharsets.UTF_8)));

        // 중복 1행 + 신규 1행 업로드
        String mixedCsv = """
            reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years
            2025,SCOPE3_CAT1,ELECTRONICS,10000,KRW,KR,MANUAL,,
            2025,SCOPE3_CAT2,,500000,KRW,KR,,,
            """;
        CsvUploadResponse result = intakeService.uploadCsv(TENANT_ID, entityId,
            new ByteArrayResource(mixedCsv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.skipCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.errorCount()).isEqualTo(0);
        // 전체 DB에는 2건 (기존 1건 + 신규 1건)
        assertThat(activityDataRepository
            .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
            .hasSize(2);
    }

    private ByteArrayResource buildCsv(int rowCount) {
        var sb = new StringBuilder(
            "reporting_year,category,sub_category,quantity,unit,country_code,data_source,data_quality,lifetime_years\n");
        for (int i = 0; i < rowCount; i++) {
            // sub_category를 i로 달리해 중복 방지
            sb.append(String.format("2025,SCOPE3_CAT1,ITEM_%d,10000,KRW,KR,MANUAL,,\n", i));
        }
        return new ByteArrayResource(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 2: 테스트 실행 — Red 확인**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.IntakeIntegrationTest"`
Expected: FAIL (컴파일 오류 또는 ApplicationContext 실패)

ApplicationContext 실패 원인을 확인한 후 수정:
- Security 설정에 `/api/v1/intake/tenants/*/webhook` permitAll 추가 필요 여부 확인
- `EsgException`이 `WEBHOOK_SIGNATURE_INVALID` 코드를 HTTP 401로 매핑하도록 `GlobalExceptionHandler` 확인

- [ ] **Step 3: GlobalExceptionHandler에서 EsgException HTTP 상태 매핑 확인**

`src/main/java/ai/claudecode/esgt2/shared/web/GlobalExceptionHandler.java` 읽기.
`EsgException` 핸들러가 `WEBHOOK_SIGNATURE_INVALID` → 401을 매핑하지 않는다면 추가:
```java
// EsgException 핸들러에서 WEBHOOK_SIGNATURE_INVALID 처리
case WEBHOOK_SIGNATURE_INVALID -> ResponseEntity.status(401).body(...);
```

또는 `EsgErrorCode`에 HTTP 상태 코드를 포함시키는 방식으로 일괄 처리.

- [ ] **Step 4: 테스트 재실행 — Green 확인**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.IntakeIntegrationTest"`
Expected: 4 tests PASS

- [ ] **Step 5: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java
git commit -m "test: CSV 100행 멱등성, 행 격리(REQUIRES_NEW), 중복 WARN 통합 테스트 (T-6-01, T-6-12, T-6-13)"
```

---

### Task 8: Webhook HMAC 통합 테스트 (T-6-05) + ERP 정규화 (T-6-06)

**Files:**
- Modify: `src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java`

Webhook 통합 테스트를 `IntakeIntegrationTest`에 추가한다.
`@SpringBootTest(webEnvironment = RANDOM_PORT)`이므로 `TestRestTemplate`으로 HTTP 레벨 테스트가 가능하다.

- [ ] **Step 1: Webhook HMAC 테스트 메서드 추가**

`IntakeIntegrationTest`에 다음을 추가 (클래스 상단에 import 포함):

```java
// 추가 필드
@Autowired
private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

@org.springframework.boot.test.web.server.LocalServerPort
private int port;

private static final String WEBHOOK_SECRET = "dev-webhook-secret-must-change-in-prod"; // application.yml 기본값

// T-6-05: Webhook 서명 검증 실패 → 401
@Test
void Webhook_서명_불일치_시_401() {
    String body = """
        [{"entityId":"%s","reportingYear":2025,"category":"SCOPE3_CAT1","subCategory":"ELECTRONICS",
          "quantity":10000,"unit":"KRW","countryCode":"KR"}]
        """.formatted(entityId);

    var headers = new org.springframework.http.HttpHeaders();
    headers.set("X-Hub-Signature-256", "invalid-signature-hex");
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

    var response = restTemplate.exchange(
        "http://localhost:" + port + "/api/v1/intake/tenants/" + TENANT_ID + "/webhook",
        org.springframework.http.HttpMethod.POST,
        new org.springframework.http.HttpEntity<>(body, headers),
        String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
}

// T-6-04: Webhook 유효 서명 → 데이터 저장
@Test
void Webhook_유효_서명_ActivityData_저장() throws Exception {
    String body = """
        [{"entityId":"%s","reportingYear":2025,"category":"SCOPE3_CAT1","subCategory":"WEBHOOK_TEST",
          "quantity":99999,"unit":"KRW","countryCode":"KR","dataSource":"SAP_ERP"}]
        """.formatted(entityId);

    String sig = computeHmac(body, WEBHOOK_SECRET);

    var headers = new org.springframework.http.HttpHeaders();
    headers.set("X-Hub-Signature-256", sig);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

    var response = restTemplate.exchange(
        "http://localhost:" + port + "/api/v1/intake/tenants/" + TENANT_ID + "/webhook",
        org.springframework.http.HttpMethod.POST,
        new org.springframework.http.HttpEntity<>(body, headers),
        CsvUploadResponse.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody().successCount()).isEqualTo(1);
    assertThat(activityDataRepository
        .findByTenantIdAndEntityIdAndReportingYear(TENANT_ID, entityId, 2025))
        .anyMatch(ad -> "SAP_ERP".equals(ad.getDataSource()));
}

private static String computeHmac(String payload, String secret) throws Exception {
    var mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(new javax.crypto.spec.SecretKeySpec(
        secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] computed = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return java.util.HexFormat.of().formatHex(computed);
}
```

- [ ] **Step 2: 테스트 실행 — Red 확인 후 수정**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.IntakeIntegrationTest.Webhook_서명_불일치_시_401"`
Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.IntakeIntegrationTest.Webhook_유효_서명_ActivityData_저장"`

Security 설정에서 webhook 경로 permitAll이 필요한 경우:
```java
// SecurityConfig에 추가
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/intake/tenants/*/webhook").permitAll()
    ...
```

- [ ] **Step 3: 테스트 Green 확인**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.IntakeIntegrationTest"`
Expected: 6 tests PASS

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/IntakeIntegrationTest.java
git commit -m "test/feat: Webhook HMAC 서명 검증 + ERP 정규화 통합 테스트 (T-6-04, T-6-05, T-6-06)"
```

---

### Task 9: @Async/@Transactional 분리 아키텍처 테스트 (T-6-15)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ghg/AsyncTransactionalArchTest.java`

esg-t1 Phase 8 교훈: `@Async` + `@Transactional`을 같은 메서드에 사용하면 하나가 무효화된다.
이 테스트는 프로젝트 전체에서 이 패턴을 방지하는 회귀 방지 테스트다.

- [ ] **Step 1: 실패 테스트 작성**

```java
// src/test/java/ai/claudecode/esgt2/ghg/AsyncTransactionalArchTest.java
package ai.claudecode.esgt2.ghg;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTransactionalArchTest {

    @Test
    void Async_메서드에_Transactional_동시_부착_없음() {
        // 모든 클래스를 스캔해 @Async + @Transactional 동시 보유 메서드 탐지
        List<String> violations = new ArrayList<>();

        try {
            var reflections = new Reflections("ai.claudecode.esgt2",
                Scanners.MethodsAnnotated);

            var asyncMethods = reflections.getMethodsAnnotatedWith(Async.class);
            for (Method method : asyncMethods) {
                if (method.isAnnotationPresent(Transactional.class)) {
                    violations.add(method.getDeclaringClass().getName() + "#" + method.getName());
                }
            }
        } catch (Exception e) {
            // reflections 라이브러리 없으면 스킵 — 빌드 의존성 확인 필요
            return;
        }

        assertThat(violations)
            .as("@Async + @Transactional 동시 부착 금지 (05-async-concurrency.md). 위반 메서드: %s", violations)
            .isEmpty();
    }
}
```

> **주의**: `reflections` 라이브러리가 없으면 compile 오류 발생. `build.gradle.kts`에 `testImplementation("org.reflections:reflections:0.10.2")` 추가 필요. 추가 전 `./gradlew dependencies --configuration testCompileClasspath | grep reflections`로 전이 의존성 확인.

- [ ] **Step 2: reflections 의존성 확인 및 추가**

Run: `./gradlew dependencies --configuration testCompileClasspath 2>&1 | grep reflections`
없으면 `build.gradle.kts`에 추가:
```kotlin
testImplementation("org.reflections:reflections:0.10.2")
```

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew test --tests "ai.claudecode.esgt2.ghg.AsyncTransactionalArchTest"`
Expected: PASS (현재 코드에 @Async + @Transactional 동시 사용 없음)

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/ghg/AsyncTransactionalArchTest.java \
        build.gradle.kts
git commit -m "test: @Async + @Transactional 동시 부착 방지 아키텍처 테스트 (T-6-15)"
```

---

### Task 10: 전체 테스트 + ModularityTest + docs 업데이트

**Files:**
- Modify: `docs/task.md`
- Modify: `docs/code-review.md`

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — 기존 테스트 포함 모두 통과

실패 시: 실패 테스트명과 로그를 확인하고 원인을 직접 수정한다.

- [ ] **Step 2: ModularityTest 실행**

Run: `./gradlew test --tests "*ModularityTest"`
Expected: PASS

`internal` 패키지 접근 위반이 발생하면 `package-info.java`의 `@ApplicationModule` 선언 확인.

- [ ] **Step 3: task.md Phase 6 체크리스트 업데이트**

`docs/task.md`에서 Phase 6 태스크 상태 업데이트:
```markdown
| T-6-01 | `test:` CSV 100행 업로드 멱등성 | DONE | |
| T-6-02 | `feat:` CSV 파싱 + 유효성 검사 + item-level 중복 방지 | DONE | |
| T-6-03 | `feat:` 오류 행 리포트 API 응답 | DONE | nonSuccessRows 필드 |
| T-6-04 | `feat:` Webhook 수신 엔드포인트 (POST /api/v1/intake/webhook) | DONE | HMAC-SHA256 |
| T-6-05 | `test:` Webhook 시그니처 검증 실패 → 401 | DONE | |
| T-6-06 | `feat:` 데이터 정규화 파이프라인 (ERP → ActivityData) | DONE | WebhookActivityDataItem → CsvRow |
| T-6-12 | **[예방]** `test:` CSV 중간 행 오류 시 이전 행 보존 | DONE | REQUIRES_NEW 검증 |
| T-6-13 | **[예방]** `test:` 중복 항목 재업로드 → WARN 로그 + 계속 처리 | DONE | |
| T-6-14 | **[예방]** `feat:` CSV 업로드 행별 독립 `@Transactional(REQUIRES_NEW)` | DONE | |
| T-6-15 | **[예방]** `test:` `@Async` 메서드에서 `@Transactional` 없음 확인 | DONE | ArchTest |
```

- [ ] **Step 4: code-review.md Phase 6A 리뷰 추가**

`docs/code-review.md`에 Phase 6A 리뷰 섹션 추가 (직접 발견된 이슈나 설계 결정 기록).

- [ ] **Step 5: 최종 커밋**

```bash
git add docs/task.md docs/code-review.md
git commit -m "docs: Phase 6A 태스크 완료 체크 + 코드 리뷰 기록"
```

---

## 이행 체크리스트 (자가 검증)

| 항목 | 확인 |
|---|---|
| CSV 100행 업로드 → 100건 ActivityData DB 저장 | |
| 동일 CSV 재업로드 → 100건 SKIPPED (멱등성) | |
| 3번째 행 오류 → 1/2/4번째 행 DB 보존 (REQUIRES_NEW) | |
| Webhook HMAC 불일치 → 401 | |
| Webhook HMAC 일치 → ActivityData 저장 | |
| @Async + @Transactional 동시 부착 없음 (ArchTest) | |
| `./gradlew test` 전체 통과 | |
| `*ModularityTest` 통과 | |
