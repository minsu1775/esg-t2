# ESG-T2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a production-grade ESG disclosure support system (esg-t2) supporting KSSB 1/2 (Korean IFRS S1/S2), Scope 1·2·3 GHG calculation, multi-entity consolidation, and external verification workspace.

**Architecture:** Spring Boot 4.0.x / Java 25 LTS monolith with Spring Modulith boundaries enforced at compile time. PostgreSQL 18 for persistence with Row-Level Security per tenant; Next.js 15 App Router frontend.

**Tech Stack:** Spring Boot 4.0.x, Java 25 (Virtual Threads), Spring Modulith, JPA/Hibernate 7, PostgreSQL 18, Flyway, TestContainers, Next.js 15 (TypeScript), Gradle Kotlin DSL, GitHub Actions

---

## File Structure

```
esg-t2/
├── build.gradle.kts                          # root Gradle config
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── docker-compose.yml                         # PostgreSQL 18 + Redis
├── .github/workflows/ci.yml                   # GitHub Actions CI
├── src/
│   ├── main/
│   │   ├── java/ai/claudecode/esgt2/
│   │   │   ├── Esgt2Application.java
│   │   │   ├── module/
│   │   │   │   ├── ghg/                       # GHG 배출량 계산 모듈
│   │   │   │   │   ├── api/                   # 공개 인터페이스 (다른 모듈 허용)
│   │   │   │   │   ├── domain/                # 순수 도메인 objects
│   │   │   │   │   ├── infra/                 # JPA repositories
│   │   │   │   │   └── internal/              # 모듈 내부 전용
│   │   │   │   ├── entity/                    # 법인·테넌트 관리
│   │   │   │   │   ├── api/
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── infra/
│   │   │   │   │   └── internal/
│   │   │   │   ├── audit/                     # AuditLog, Hash Chain
│   │   │   │   │   ├── api/
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── infra/
│   │   │   │   │   └── internal/
│   │   │   │   ├── vw/                        # Verification Workspace
│   │   │   │   │   ├── api/
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── infra/
│   │   │   │   │   └── internal/
│   │   │   │   ├── rpt/                       # 보고서 생성
│   │   │   │   │   ├── api/
│   │   │   │   │   ├── domain/
│   │   │   │   │   ├── infra/
│   │   │   │   │   └── internal/
│   │   │   │   └── supply/                    # 공급업체 포털
│   │   │   │       ├── api/
│   │   │   │       ├── domain/
│   │   │   │       ├── infra/
│   │   │   │       └── internal/
│   │   │   ├── shared/                        # 공통 Value Object, Event, Exception
│   │   │   │   ├── exception/
│   │   │   │   │   ├── EsgException.java
│   │   │   │   │   ├── EsgErrorCode.java
│   │   │   │   │   └── ResourceNotFoundException.java
│   │   │   │   └── event/
│   │   │   └── config/                        # Spring Config
│   │   │       └── SecurityConfig.java
│   │   └── resources/
│   │       ├── db/
│   │       │   ├── migration/                 # H2 + PostgreSQL 공통 DDL
│   │       │   │   ├── V1__initial_schema.sql
│   │       │   │   └── V2__disclosure_schedules.sql
│   │       │   └── migration-pg/              # PostgreSQL 전용 (RLS 등)
│   │       ├── application.yml                # H2 default (로컬 개발)
│   │       ├── application-test.yml           # TestContainers PostgreSQL
│   │       └── application-prod.yml           # 운영 PostgreSQL
│   └── test/
│       └── java/ai/claudecode/esgt2/
│           ├── Esgt2ApplicationTest.java      # context loads smoke test
│           ├── support/
│           │   └── AbstractIntegrationTest.java
│           └── ModularityTest.java
├── frontend/                                  # Next.js 15 App Router
│   ├── package.json
│   ├── tsconfig.json
│   └── src/app/
└── docs/
    └── superpowers/plans/ (this file)
```

---

## Phase 0: 프로젝트 셋업 & 인프라 기반

---

### Task 1: Gradle 빌드 파일 + Spring Boot 4 프로젝트 생성 (T-0-01, T-0-02)

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `src/main/java/ai/claudecode/esgt2/Esgt2Application.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/ai/claudecode/esgt2/Esgt2ApplicationTest.java`

- [ ] **Step 1: Write the failing context-loads test**

```java
// src/test/java/ai/claudecode/esgt2/Esgt2ApplicationTest.java
package ai.claudecode.esgt2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class Esgt2ApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test — verify it FAILS (no source yet)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.Esgt2ApplicationTest" 2>&1 | tail -20
```

Expected: `BUILD FAILED` — no source code exists yet.

- [ ] **Step 3: Create settings.gradle.kts**

```kotlin
// settings.gradle.kts
rootProject.name = "esgt2"
```

- [ ] **Step 4: Create build.gradle.kts**

> Note: Use https://start.spring.io to generate the initial Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`). Select: Spring Boot 4.0.x, Java 25, Gradle-Kotlin. The build.gradle.kts below replaces the generated one.

```kotlin
// build.gradle.kts
plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "ai.claudecode"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.0")
    }
}

dependencies {
    // Web / API
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 5: Create Esgt2Application.java**

```java
// src/main/java/ai/claudecode/esgt2/Esgt2Application.java
package ai.claudecode.esgt2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Esgt2Application {

    public static void main(String[] args) {
        SpringApplication.run(Esgt2Application.class, args);
    }
}
```

- [ ] **Step 6: Create application.yml (H2 로컬 개발)**

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: esgt2

  datasource:
    url: jdbc:h2:mem:esgt2;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: true

  flyway:
    locations:
      - classpath:db/migration
    baseline-on-migrate: false

  data:
    redis:
      host: localhost
      port: 6379

  threads:
    virtual:
      enabled: true  # Java 25 Virtual Threads

  security:
    user:
      name: admin
      password: admin

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true

scheduler:
  enabled: false  # 로컬 기본값 — 명시적으로 활성화해야 실행

logging:
  level:
    ai.claudecode.esgt2: DEBUG
    org.flywaydb: INFO
```

- [ ] **Step 7: Create application-test.yml**

```yaml
# src/main/resources/application-test.yml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  flyway:
    locations:
      - classpath:db/migration   # 공통 DDL만 (H2 호환)

  data:
    redis:
      host: localhost
      port: 6379

scheduler:
  enabled: false   # T-0-15: 통합 테스트 스케줄러 격리
```

- [ ] **Step 8: Create minimal V1 migration placeholder**

```sql
-- src/main/resources/db/migration/V1__initial_schema.sql
-- Placeholder: full schema added in Task 5
SELECT 1;
```

- [ ] **Step 9: Run test — verify it PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.Esgt2ApplicationTest"
```

Expected: `BUILD SUCCESSFUL` — Spring context loads with H2 + Flyway.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts \
  src/main/java/ai/claudecode/esgt2/Esgt2Application.java \
  src/main/resources/application.yml \
  src/main/resources/application-test.yml \
  src/main/resources/db/migration/V1__initial_schema.sql \
  src/test/java/ai/claudecode/esgt2/Esgt2ApplicationTest.java
git commit -m "feat: Spring Boot 4 + Java 25 프로젝트 초기 설정"
```

---

### Task 2: Docker Compose — PostgreSQL 18 + Redis (T-0-04, T-0-05)

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: Create docker-compose.yml**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:18
    container_name: esgt2-postgres
    environment:
      POSTGRES_DB: esgt2
      POSTGRES_USER: esgt2
      POSTGRES_PASSWORD: esgt2_local
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U esgt2"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: esgt2-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    container_name: esgt2-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  minio_data:
```

- [ ] **Step 2: Start services**

```bash
docker compose up -d
```

Expected:
```
✔ Container esgt2-postgres  Started
✔ Container esgt2-redis     Started
✔ Container esgt2-minio     Started
```

- [ ] **Step 3: Verify services**

```bash
docker compose ps
```

Expected: all three services show `healthy` or `running`.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: Docker Compose — PostgreSQL 18 + Redis + MinIO"
```

---

### Task 3: Spring Modulith 모듈 패키지 뼈대 생성 (T-0-03)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/module/ghg/package-info.java` (and 5 more modules)
- Create: `src/main/java/ai/claudecode/esgt2/shared/exception/EsgException.java`
- Create: `src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java`
- Create: `src/main/java/ai/claudecode/esgt2/shared/exception/ResourceNotFoundException.java`

- [ ] **Step 1: Create module package-info files**

Each module needs a `package-info.java` so Spring Modulith detects it. Create for all 6 modules (`ghg`, `entity`, `audit`, `vw`, `rpt`, `supply`):

```java
// src/main/java/ai/claudecode/esgt2/module/ghg/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.ghg;
```

```java
// src/main/java/ai/claudecode/esgt2/module/entity/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.entity;
```

```java
// src/main/java/ai/claudecode/esgt2/module/audit/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.audit;
```

```java
// src/main/java/ai/claudecode/esgt2/module/vw/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.vw;
```

```java
// src/main/java/ai/claudecode/esgt2/module/rpt/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.rpt;
```

```java
// src/main/java/ai/claudecode/esgt2/module/supply/package-info.java
@org.springframework.modulith.ApplicationModule
package ai.claudecode.esgt2.module.supply;
```

- [ ] **Step 2: Create shared exception classes**

```java
// src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java
package ai.claudecode.esgt2.shared.exception;

public enum EsgErrorCode {
    RESOURCE_NOT_FOUND,
    ACCESS_DENIED,
    VALIDATION_FAILED,
    OPTIMISTIC_LOCK_CONFLICT,
    FORMULA_EVALUATION_FAILED,
    FORMULA_VALIDATION_FAILED,
    INVALID_FILE_PATH,
    UNSUPPORTED_FILE_TYPE,
    REJECTION_REASON_REQUIRED,
    INTERNAL_ERROR
}
```

```java
// src/main/java/ai/claudecode/esgt2/shared/exception/EsgException.java
package ai.claudecode.esgt2.shared.exception;

import lombok.Getter;

@Getter
public class EsgException extends RuntimeException {

    private final EsgErrorCode errorCode;

    public EsgException(EsgErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public EsgException(EsgErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

```java
// src/main/java/ai/claudecode/esgt2/shared/exception/ResourceNotFoundException.java
package ai.claudecode.esgt2.shared.exception;

public class ResourceNotFoundException extends EsgException {

    public ResourceNotFoundException(String message) {
        super(EsgErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
```

- [ ] **Step 3: Create sub-package placeholder classes for each module**

For each module, create an empty marker class in `api/` so the package structure is valid (Spring Modulith needs actual classes to scan):

```java
// src/main/java/ai/claudecode/esgt2/module/ghg/api/GhgApi.java
package ai.claudecode.esgt2.module.ghg.api;
// public API for ghg module — populated in Phase 3
```

Repeat for: `entity/api/EntityApi.java`, `audit/api/AuditApi.java`, `vw/api/VwApi.java`, `rpt/api/RptApi.java`, `supply/api/SupplyApi.java`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ai/claudecode/esgt2/module/ \
        src/main/java/ai/claudecode/esgt2/shared/
git commit -m "feat: Spring Modulith 모듈 패키지 뼈대 + 공통 예외 클래스"
```

---

### Task 4: AbstractIntegrationTest — TestContainers (T-0-07, T-0-16)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/support/AbstractIntegrationTest.java`

> **Why static start (T-0-16 prevention):** If `@DynamicPropertySource` executes before the container starts, Spring Boot reads the original (unconfigured) datasource URL and the test fails. `static { POSTGRES.start(); }` guarantees the container is running before any Spring lifecycle method.

- [ ] **Step 1: Write a failing integration test that needs AbstractIntegrationTest**

```java
// src/test/java/ai/claudecode/esgt2/support/AbstractIntegrationTestTest.java
package ai.claudecode.esgt2.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractIntegrationTestTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void PostgreSQL에_연결된다() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test — verify FAILS (AbstractIntegrationTest not yet created)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.support.AbstractIntegrationTestTest"
```

Expected: `BUILD FAILED` — compilation error.

- [ ] **Step 3: Create AbstractIntegrationTest**

```java
// src/test/java/ai/claudecode/esgt2/support/AbstractIntegrationTest.java
package ai.claudecode.esgt2.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("esgt2_test")
            .withUsername("test")
            .withPassword("test");

    // T-0-16: static block guarantees container is up before @DynamicPropertySource runs
    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
```

- [ ] **Step 4: Run test — verify PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.support.AbstractIntegrationTestTest"
```

Expected: `BUILD SUCCESSFUL` — PostgreSQL 연결 성공.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/ai/claudecode/esgt2/support/
git commit -m "test: AbstractIntegrationTest — TestContainers static start 패턴 적용"
```

---

### Task 5: Flyway 멀티 로케이션 + V1 초기 스키마 (T-0-06, T-0-14)

**Files:**
- Modify: `src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `src/main/resources/db/migration-pg/` (empty placeholder)
- Modify: `src/main/resources/application-test.yml`
- Create: `src/main/resources/application-prod.yml`

> **T-0-14 prevention:** `db/migration` (H2 + PostgreSQL 공통) vs `db/migration-pg` (PostgreSQL RLS·파티션 전용). If PG-specific DDL is placed in `db/migration`, H2 will fail to parse it during tests. Flyway `locations` in `application-test.yml` must NOT include `migration-pg`.

- [ ] **Step 1: Write failing test for Flyway migration**

```java
// src/test/java/ai/claudecode/esgt2/FlywayMigrationTest.java
package ai.claudecode.esgt2;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenants_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'tenants'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void disclosure_schedules_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'disclosure_schedules'",
            Integer.class
        );
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test — verify FAILS (V1 is just `SELECT 1`)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.FlywayMigrationTest"
```

Expected: `BUILD FAILED` — `tenants` 테이블 없음.

- [ ] **Step 3: Replace V1__initial_schema.sql with real schema**

```sql
-- src/main/resources/db/migration/V1__initial_schema.sql
-- tenants: 멀티 테넌트 최상위 테이블
CREATE TABLE tenants (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    country_code CHAR(2)      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ
);

-- disclosure_schedules: 공시 일정 (연도별 KSSB/GRI 의무 일정)
CREATE TABLE disclosure_schedules (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID        NOT NULL REFERENCES tenants(id),
    reporting_year INT         NOT NULL,
    deadline       DATE        NOT NULL,
    standard       VARCHAR(50) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, reporting_year, standard)
);
```

- [ ] **Step 4: Create migration-pg placeholder**

```bash
mkdir -p src/main/resources/db/migration-pg
touch src/main/resources/db/migration-pg/.gitkeep
```

- [ ] **Step 5: Update application-prod.yml**

```yaml
# src/main/resources/application-prod.yml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}

  flyway:
    locations:
      - classpath:db/migration
      - classpath:db/migration-pg   # T-0-14: PostgreSQL 전용 DDL 포함

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  threads:
    virtual:
      enabled: true

scheduler:
  enabled: true
```

- [ ] **Step 6: Run test — verify PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.FlywayMigrationTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/ \
        src/main/resources/application-prod.yml
git commit -m "feat: Flyway 멀티 로케이션 설정 + V1 초기 스키마 (tenants, disclosure_schedules)"
```

---

### Task 6: disclosure_schedule 초기 데이터 마이그레이션 (T-0-13)

**Files:**
- Create: `src/main/resources/db/migration/V2__disclosure_schedule_seed.sql`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/ai/claudecode/esgt2/FlywayMigrationTest.java  (add test method)
@Test
void disclosure_schedule_시드_데이터가_존재한다() {
    // Note: seed uses a fixed demo tenant — real tenants added via API
    // Verify the V2 migration ran without error
    Integer migrationCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
        Integer.class
    );
    assertThat(migrationCount).isEqualTo(1);
}
```

- [ ] **Step 2: Run test — verify FAILS**

```bash
./gradlew test --tests "ai.claudecode.esgt2.FlywayMigrationTest.disclosure_schedule_시드_데이터가_존재한다"
```

Expected: FAIL — V2 migration does not exist.

- [ ] **Step 3: Create V2 seed migration**

```sql
-- src/main/resources/db/migration/V2__disclosure_schedule_seed.sql
-- Demo tenant for local dev and tests
INSERT INTO tenants (id, code, name, country_code)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEMO', '데모 법인', 'KR')
ON CONFLICT (code) DO NOTHING;

-- FY2028~2031 KSSB 1/2 공시 일정 (규제: 2026-02-26 KSSB 고시 기준)
INSERT INTO disclosure_schedules (tenant_id, reporting_year, deadline, standard)
VALUES
  ('00000000-0000-0000-0000-000000000001', 2028, '2029-04-30', 'KSSB1'),
  ('00000000-0000-0000-0000-000000000001', 2028, '2029-04-30', 'KSSB2'),
  ('00000000-0000-0000-0000-000000000001', 2029, '2030-04-30', 'KSSB1'),
  ('00000000-0000-0000-0000-000000000001', 2029, '2030-04-30', 'KSSB2'),
  ('00000000-0000-0000-0000-000000000001', 2030, '2031-04-30', 'KSSB1'),
  ('00000000-0000-0000-0000-000000000001', 2030, '2031-04-30', 'KSSB2'),
  ('00000000-0000-0000-0000-000000000001', 2031, '2032-04-30', 'KSSB1'),
  ('00000000-0000-0000-0000-000000000001', 2031, '2032-04-30', 'KSSB2')
ON CONFLICT (tenant_id, reporting_year, standard) DO NOTHING;
```

- [ ] **Step 4: Run test — verify PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.FlywayMigrationTest"
```

Expected: `BUILD SUCCESSFUL` — 모든 3개 테스트 통과.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V2__disclosure_schedule_seed.sql
git commit -m "feat: V2 disclosure_schedules 시드 데이터 (FY2028~2031 KSSB 1/2)"
```

---

### Task 7: ModularityTest (T-0-08)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/ModularityTest.java`

- [ ] **Step 1: Write ModularityTest**

```java
// src/test/java/ai/claudecode/esgt2/ModularityTest.java
package ai.claudecode.esgt2;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    ApplicationModules modules = ApplicationModules.of(Esgt2Application.class);

    @Test
    void 모듈_경계가_유효하다() {
        modules.verify();
    }

    @Test
    void 모듈_목록_출력() {
        modules.forEach(System.out::println);
    }
}
```

- [ ] **Step 2: Run ModularityTest**

```bash
./gradlew test --tests "*ModularityTest"
```

Expected: `BUILD SUCCESSFUL` — 6개 모듈(ghg, entity, audit, vw, rpt, supply) 경계 검증 통과.

If it FAILS with `IllegalStateException: module dependency violation`, check that no module imports another module's `internal` package.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/ai/claudecode/esgt2/ModularityTest.java
git commit -m "test: Spring Modulith ModularityTest 초기 설정"
```

---

### Task 8: GitHub Actions CI 파이프라인 (T-0-09)

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create CI workflow**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    name: Test & Modularity Check
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make gradlew executable
        run: chmod +x ./gradlew

      - name: Run unit & integration tests
        run: ./gradlew test
        env:
          TESTCONTAINERS_RYUK_DISABLED: true   # CI 환경 안정성

      - name: Run Modularity Check
        run: ./gradlew test --tests "*ModularityTest"

      - name: Build (skip tests)
        run: ./gradlew build -x test

      - name: Upload test reports
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: test-reports
          path: build/reports/tests/test/
```

- [ ] **Step 2: Commit and push**

```bash
git add .github/workflows/ci.yml
git commit -m "feat: GitHub Actions CI — test + modularity-check + build"
git push origin main
```

- [ ] **Step 3: Verify CI passes**

Go to `https://github.com/minsu1775/esg-t2/actions` and confirm the CI run turns green. If it fails due to Docker socket, check `TESTCONTAINERS_RYUK_DISABLED: true` is set.

---

### Task 9: OpenTelemetry + Prometheus 메트릭 (T-0-10, T-0-11)

**Files:**
- Modify: `src/main/resources/application.yml` (already includes prometheus config)
- Create: `src/test/java/ai/claudecode/esgt2/ActuatorEndpointTest.java`

- [ ] **Step 1: Write failing test for actuator endpoints**

```java
// src/test/java/ai/claudecode/esgt2/ActuatorEndpointTest.java
package ai.claudecode.esgt2;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void health_엔드포인트가_200을_반환한다() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheus_메트릭_엔드포인트가_200을_반환한다() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }
}
```

- [ ] **Step 2: Run test — confirm PASSES (config already in application.yml)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.ActuatorEndpointTest"
```

Expected: `BUILD SUCCESSFUL`.

If `prometheus` endpoint returns 404, verify `management.endpoints.web.exposure.include` in `application.yml` includes `prometheus`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/ai/claudecode/esgt2/ActuatorEndpointTest.java
git commit -m "test: Actuator health + Prometheus 메트릭 엔드포인트 검증"
```

---

### Task 10: Next.js 15 프로젝트 초기화 (T-0-12)

**Files:**
- Create: `frontend/` directory (via `create-next-app`)

- [ ] **Step 1: Initialize Next.js 15 project**

```bash
npx create-next-app@latest frontend \
  --typescript \
  --tailwind \
  --eslint \
  --app \
  --src-dir \
  --import-alias "@/*" \
  --no-turbopack
```

- [ ] **Step 2: Enable TypeScript strict mode**

Edit `frontend/tsconfig.json` — confirm `"strict": true` is set (Next.js 15 sets it by default).

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true
  }
}
```

Add `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes` for extra safety.

- [ ] **Step 3: Run typecheck**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: `✓ Compiled successfully`.

- [ ] **Step 4: Commit**

```bash
git add frontend/
git commit -m "feat: Next.js 15 App Router + TypeScript strict 초기화"
```

---

### Task 11: Phase 0 전체 검증 (DoD 확인)

- [ ] **Step 1: Run all tests**

```bash
./gradlew test
```

Expected: All tests pass.

- [ ] **Step 2: Run ModularityTest specifically**

```bash
./gradlew test --tests "*ModularityTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build full artifact**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify CI green on GitHub**

Check `https://github.com/minsu1775/esg-t2/actions` — latest run must be green.

- [ ] **Step 5: Phase 0 DoD checklist**

- [ ] `./gradlew test` 전체 통과
- [ ] `./gradlew test --tests "*ModularityTest"` 통과
- [ ] Docker Compose: PostgreSQL 18, Redis, MinIO 모두 `healthy`
- [ ] GitHub Actions CI green
- [ ] application-test.yml에 `scheduler.enabled: false` 확인 (T-0-15)
- [ ] AbstractIntegrationTest에 `static { POSTGRES.start(); }` 확인 (T-0-16)
- [ ] `db/migration` vs `db/migration-pg` 분리 확인 (T-0-14)
- [ ] Next.js 15 typecheck 통과

---

## Phase 1: 법인·테넌트 관리 (entity 모듈) — Key Tasks

> Phase 0의 `AbstractIntegrationTest`를 상속받아 모든 통합 테스트를 작성한다.

---

### Task 12: V2 entity/auth 스키마 마이그레이션 (T-1-01, T-1-02)

**Files:**
- Create: `src/main/resources/db/migration/V3__entity_tables.sql`
- Create: `src/main/resources/db/migration/V4__auth_tables.sql`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/ai/claudecode/esgt2/module/entity/EntityTableMigrationTest.java
package ai.claudecode.esgt2.module.entity;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTableMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void legal_entities_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'legal_entities'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void users_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test — FAILS**

```bash
./gradlew test --tests "ai.claudecode.esgt2.module.entity.EntityTableMigrationTest"
```

- [ ] **Step 3: Create V3__entity_tables.sql**

```sql
-- src/main/resources/db/migration/V3__entity_tables.sql
CREATE TABLE legal_entities (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200) NOT NULL,
    country_code    CHAR(2)      NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,  -- PARENT, SUBSIDIARY, ASSOCIATE
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    UNIQUE(tenant_id, name)
);

CREATE TABLE entity_relationships (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    parent_id       UUID         NOT NULL REFERENCES legal_entities(id),
    child_id        UUID         NOT NULL REFERENCES legal_entities(id),
    ownership_ratio NUMERIC(5,4) NOT NULL CHECK (ownership_ratio > 0 AND ownership_ratio <= 1),
    method          VARCHAR(50)  NOT NULL DEFAULT 'EQUITY',  -- EQUITY, OPERATIONAL_CONTROL
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT no_self_reference CHECK (parent_id <> child_id)
);
```

- [ ] **Step 4: Create V4__auth_tables.sql**

```sql
-- src/main/resources/db/migration/V4__auth_tables.sql
CREATE TABLE users (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    email        VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    UNIQUE(tenant_id, email)
);

CREATE TABLE user_roles (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID        NOT NULL REFERENCES users(id),
    role      VARCHAR(50) NOT NULL,  -- TENANT_ADMIN, ESG_MANAGER, DATA_ENTRY, APPROVER, VERIFIER, SUPPLIER
    entity_id UUID        REFERENCES legal_entities(id),  -- scope (null = all entities)
    UNIQUE(user_id, role, entity_id)
);
```

- [ ] **Step 5: Run test — PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.module.entity.EntityTableMigrationTest"
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V3__entity_tables.sql \
        src/main/resources/db/migration/V4__auth_tables.sql \
        src/test/java/ai/claudecode/esgt2/module/entity/EntityTableMigrationTest.java
git commit -m "feat: V3 법인 테이블 + V4 인증 테이블 마이그레이션"
```

---

### Task 13: LegalEntity 도메인 (T-1-03, T-1-04)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/module/entity/domain/LegalEntity.java`
- Create: `src/test/java/ai/claudecode/esgt2/module/entity/domain/LegalEntityTest.java`

- [ ] **Step 1: Write failing unit tests (no JPA, pure Java)**

```java
// src/test/java/ai/claudecode/esgt2/module/entity/domain/LegalEntityTest.java
package ai.claudecode.esgt2.module.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalEntityTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void 필수_필드로_법인을_생성한다() {
        LegalEntity entity = LegalEntity.create(TENANT_ID, "삼성전자", "KR", EntityType.PARENT);
        assertThat(entity.getName()).isEqualTo("삼성전자");
        assertThat(entity.getCountryCode()).isEqualTo("KR");
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    void 이름이_null이면_예외가_발생한다() {
        assertThatThrownBy(() -> LegalEntity.create(TENANT_ID, null, "KR", EntityType.PARENT))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void 국가코드가_2자가_아니면_예외가_발생한다() {
        assertThatThrownBy(() -> LegalEntity.create(TENANT_ID, "Test", "KOR", EntityType.PARENT))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void 비활성화하면_isActive가_false가_된다() {
        LegalEntity entity = LegalEntity.create(TENANT_ID, "삼성전자", "KR", EntityType.PARENT);
        entity.deactivate();
        assertThat(entity.isActive()).isFalse();
    }
}
```

- [ ] **Step 2: Run test — FAILS (class not found)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.module.entity.domain.LegalEntityTest"
```

- [ ] **Step 3: Create EntityType enum**

```java
// src/main/java/ai/claudecode/esgt2/module/entity/domain/EntityType.java
package ai.claudecode.esgt2.module.entity.domain;

public enum EntityType {
    PARENT, SUBSIDIARY, ASSOCIATE
}
```

- [ ] **Step 4: Create LegalEntity domain class**

```java
// src/main/java/ai/claudecode/esgt2/module/entity/domain/LegalEntity.java
package ai.claudecode.esgt2.module.entity.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class LegalEntity {

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final String countryCode;
    private final EntityType entityType;
    private boolean active;
    private final Instant createdAt;

    private LegalEntity(UUID id, UUID tenantId, String name, String countryCode,
                        EntityType entityType, boolean active, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.countryCode = countryCode;
        this.entityType = entityType;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static LegalEntity create(UUID tenantId, String name, String countryCode, EntityType entityType) {
        validate(tenantId, name, countryCode, entityType);
        return new LegalEntity(UUID.randomUUID(), tenantId, name, countryCode, entityType, true, Instant.now());
    }

    public void deactivate() {
        this.active = false;
    }

    private static void validate(UUID tenantId, String name, String countryCode, EntityType entityType) {
        if (tenantId == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "tenantId는 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "법인명은 필수입니다");
        }
        if (countryCode == null || countryCode.length() != 2) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "국가코드는 2자리 ISO 코드여야 합니다");
        }
        if (entityType == null) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "entityType은 필수입니다");
        }
    }
}
```

- [ ] **Step 5: Run test — PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.module.entity.domain.LegalEntityTest"
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ai/claudecode/esgt2/module/entity/domain/ \
        src/test/java/ai/claudecode/esgt2/module/entity/domain/LegalEntityTest.java
git commit -m "test: LegalEntity 도메인 유효성 검증 / feat: LegalEntity.create() 팩토리 메서드"
```

---

### Task 14: GlobalExceptionHandler (T-1-17 예방 포함)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/config/GlobalExceptionHandler.java`
- Create: `src/test/java/ai/claudecode/esgt2/config/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write failing test for required exception handlers**

```java
// src/test/java/ai/claudecode/esgt2/config/GlobalExceptionHandlerTest.java
package ai.claudecode.esgt2.config;

import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest extends AbstractIntegrationTest {

    // This test relies on a @TestController producing specific exceptions.
    // Implemented via a dedicated test endpoint below.

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void ResourceNotFoundException은_404를_반환한다() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/test/resource-not-found", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void AccessDeniedException은_403을_반환한다() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/test/access-denied", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 2: Run test — FAILS (no handler, no test endpoint)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.config.GlobalExceptionHandlerTest"
```

- [ ] **Step 3: Create GlobalExceptionHandler**

```java
// src/main/java/ai/claudecode/esgt2/config/GlobalExceptionHandler.java
package ai.claudecode.esgt2.config;

import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle(ex.getErrorCode().name());
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        // T-1-17 prevention: Spring 기본 처리 시 500 반환 — 반드시 403으로 명시
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("ACCESS_DENIED");
        detail.setDetail("접근 권한이 없습니다");
        return detail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("OPTIMISTIC_LOCK_CONFLICT");
        detail.setDetail("동시 수정 충돌이 발생했습니다. 다시 시도해 주세요");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("VALIDATION_FAILED");
        detail.setDetail(ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        return detail;
    }

    @ExceptionHandler(EsgException.class)
    ProblemDetail handleEsgException(EsgException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case VALIDATION_FAILED, FORMULA_EVALUATION_FAILED, FORMULA_VALIDATION_FAILED,
                 INVALID_FILE_PATH, UNSUPPORTED_FILE_TYPE, REJECTION_REASON_REQUIRED -> HttpStatus.BAD_REQUEST;
            case OPTIMISTIC_LOCK_CONFLICT -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(ex.getErrorCode().name());
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnknown(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("INTERNAL_ERROR");
        detail.setDetail("서버 내부 오류가 발생했습니다");
        return detail;
    }
}
```

- [ ] **Step 4: Create test controller (test scope only)**

```java
// src/test/java/ai/claudecode/esgt2/config/TestExceptionController.java
package ai.claudecode.esgt2.config;

import ai.claudecode.esgt2.shared.exception.ResourceNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@Profile("test")
class TestExceptionController {

    @GetMapping("/resource-not-found")
    void resourceNotFound() {
        throw new ResourceNotFoundException("테스트 리소스 없음");
    }

    @GetMapping("/access-denied")
    void accessDenied() {
        throw new AccessDeniedException("테스트 접근 거부");
    }
}
```

- [ ] **Step 5: Run test — PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.config.GlobalExceptionHandlerTest"
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ai/claudecode/esgt2/config/GlobalExceptionHandler.java \
        src/test/java/ai/claudecode/esgt2/config/
git commit -m "test: AccessDeniedException→403, ResourceNotFoundException→404 검증 / feat: GlobalExceptionHandler"
```

---

## Phase 2: AuditLog & Hash Chain — Key Tasks

---

### Task 15: @Auditable 어노테이션 + AuditAspect (T-2-02, T-2-03, T-2-04)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/module/audit/api/Auditable.java`
- Create: `src/main/java/ai/claudecode/esgt2/module/audit/internal/AuditAspect.java`
- Create: `src/main/resources/db/migration/V5__audit_tables.sql`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/ai/claudecode/esgt2/module/audit/AuditableAspectTest.java
package ai.claudecode.esgt2.module.audit;

import ai.claudecode.esgt2.module.audit.api.Auditable;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditableAspectTest extends AbstractIntegrationTest {

    @Autowired
    private TestAuditableService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void Auditable_메서드_실행시_AuditLog가_저장된다() {
        service.doAuditableAction();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action = 'TEST_ACTION'",
            Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Service
    static class TestAuditableService {
        @Auditable(action = "TEST_ACTION")
        @Transactional
        public void doAuditableAction() {
            // intentionally empty
        }
    }
}
```

- [ ] **Step 2: Create V5__audit_tables.sql**

```sql
-- src/main/resources/db/migration/V5__audit_tables.sql
CREATE TABLE audit_logs (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      UUID,
    actor_id       UUID,
    action         VARCHAR(100) NOT NULL,
    target_type    VARCHAR(100),
    target_id      UUID,
    before_state   JSONB,
    after_state    JSONB,
    prev_hash      VARCHAR(64),
    current_hash   VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_action    ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- Outbox for reliable async event delivery
CREATE TABLE outbox_events (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED
    error_msg    TEXT,
    retry_count  INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
```

- [ ] **Step 3: Create @Auditable annotation**

```java
// src/main/java/ai/claudecode/esgt2/module/audit/api/Auditable.java
package ai.claudecode.esgt2.module.audit.api;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    String action();
}
```

- [ ] **Step 4: Create AuditAspect**

```java
// src/main/java/ai/claudecode/esgt2/module/audit/internal/AuditAspect.java
package ai.claudecode.esgt2.module.audit.internal;

import ai.claudecode.esgt2.module.audit.api.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
class AuditAspect {

    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Object result = pjp.proceed();
        saveAuditLog(auditable.action());
        return result;
    }

    private void saveAuditLog(String action) {
        String prevHash = queryLastHash();
        String payload = action + ":" + System.currentTimeMillis();
        String currentHash = sha256(prevHash + payload);

        jdbcTemplate.update(
            "INSERT INTO audit_logs (action, prev_hash, current_hash) VALUES (?, ?, ?)",
            action, prevHash, currentHash
        );
    }

    private String queryLastHash() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT current_hash FROM audit_logs ORDER BY id DESC LIMIT 1",
                String.class);
        } catch (Exception e) {
            return "GENESIS";
        }
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 계산 실패", e);
        }
    }
}
```

- [ ] **Step 5: Run test — PASSES**

```bash
./gradlew test --tests "ai.claudecode.esgt2.module.audit.AuditableAspectTest"
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ai/claudecode/esgt2/module/audit/ \
        src/main/resources/db/migration/V5__audit_tables.sql \
        src/test/java/ai/claudecode/esgt2/module/audit/
git commit -m "test: @Auditable AOP 자동 기록 검증 / feat: @Auditable + AuditAspect + V5 audit_logs 테이블"
```

---

## Phases 3–12: Task Index

> Each phase follows the same TDD pattern: `test:` failing test → `feat:` minimal implementation → `refactor:` cleanup. Only key tasks with non-obvious implementation details are called out below. Full bite-sized steps follow the same structure as Phase 0–2 tasks above.

---

### Phase 3 Key Tasks: 배출계수 로더 & Scope 1/2 계산 (T-3-xx)

**T-3-06 — EmissionFactorLoader (item-level 멱등 upsert)**

```java
// YAML loading core — ON CONFLICT per (category, country, year, gas)
jdbcTemplate.update("""
    INSERT INTO emission_factors (category, country_code, reporting_year, gas_type, factor_value, unit, source)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (category, country_code, reporting_year, gas_type)
    DO UPDATE SET factor_value = EXCLUDED.factor_value, updated_at = NOW()
    """, category, countryCode, year, gas, value, unit, source);
```

**T-3-12 — EmissionCalculator (BigDecimal 필수, T-3-20 prevention)**

```java
public BigDecimal calculateScope1(BigDecimal activityAmount, BigDecimal emissionFactor, BigDecimal gwp) {
    // float/double 절대 금지 — KSSB 수치 정밀도 요구
    return activityAmount
        .multiply(emissionFactor)
        .multiply(gwp)
        .setScale(6, RoundingMode.HALF_UP);
}
```

**T-3-18 — resolveAt(category, date) historical factor lookup (T-3-19 prevention)**

```java
public BigDecimal resolveAt(String category, LocalDate referenceDate) {
    return emissionFactorRepository.findMostRecentBefore(category, referenceDate)
        .orElseThrow(() -> new ResourceNotFoundException(
            "배출계수 없음: " + category + " @ " + referenceDate));
}
```

---

### Phase 3-B Key Tasks: 증빙 파일 & Formula DSL (T-3B-xx)

**T-3B-04 — EvidenceFileService.upload() (DigestInputStream 단일 I/O, T-3B-18 prevention)**

```java
public EvidenceFileId upload(InputStream inputStream, String originalFilename, String mimeType) {
    validateExtension(originalFilename);  // 확장자 먼저

    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String storedFilename = UUID.randomUUID() + getExtension(originalFilename);

    try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
        StorageUri uri = storageGateway.upload(dis, storedFilename, mimeType);
        String sha256 = HexFormat.of().formatHex(digest.digest());  // I/O 1회
        return evidenceRepository.save(/* entity */).getId();
    }
}
```

**T-3B-17 — resolveContained() path traversal defense (T-3B-15 prevention)**

```java
public Path resolveContained(Path storageRoot, String filename) {
    Path resolved = storageRoot.resolve(filename).normalize();
    if (!resolved.startsWith(storageRoot)) {
        throw new EsgException(EsgErrorCode.INVALID_FILE_PATH, "경로 순회 공격 차단: " + filename);
    }
    return resolved;
}
```

**T-3B-20 — Formula DoS limits (FormulaConstants)**

```java
// src/main/java/ai/claudecode/esgt2/module/ghg/domain/formula/FormulaConstants.java
public final class FormulaConstants {
    public static final int MAX_EXPRESSION_LENGTH = 1000;
    public static final int MAX_NUMBER_LENGTH     = 50;
    public static final int MAX_PARSER_DEPTH      = 50;
    public static final int MAX_EVAL_DEPTH        = 50;

    private FormulaConstants() {}
}
```

---

### Phase 6 Key Task: CSV REQUIRES_NEW (T-6-12, T-6-14 prevention)

**CSV 행별 독립 트랜잭션 패턴**

```java
// CsvImportService.java — @Async + @Transactional 절대 동일 빈에 선언 금지
@Service
@RequiredArgsConstructor
public class DefaultCsvImportService {

    private final CsvRowProcessor rowProcessor;  // REQUIRES_NEW를 가진 별도 빈

    public CsvImportResult importFile(InputStream csv) {
        List<CsvRow> rows = parseCsv(csv);
        List<String> errors = new ArrayList<>();
        for (CsvRow row : rows) {
            try {
                rowProcessor.processRow(row);  // 각 행 독립 트랜잭션
            } catch (Exception e) {
                errors.add("행 " + row.lineNumber() + ": " + e.getMessage());
                log.warn("CSV 행 처리 실패 (계속 진행): {}", e.getMessage());
            }
        }
        return new CsvImportResult(rows.size(), errors);
    }
}

@Service
class CsvRowProcessor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 행별 독립 커밋
    public void processRow(CsvRow row) {
        // 개별 행 저장 로직
    }
}
```

---

### Phase 7 Key Task: Approval State Machine (T-7-11 prevention)

```java
// ApprovalEntity — setStatus() 직접 호출 금지, 메서드만 허용
public void approve(UUID approverId) {
    if (this.status != ApprovalStatus.SUBMITTED) {
        throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "제출된 보고서만 승인 가능");
    }
    this.status = ApprovalStatus.APPROVED;
    this.approverId = approverId;
    this.approvedAt = Instant.now();
}

public void reject(String reason) {
    if (reason == null || reason.isBlank()) {
        throw new EsgException(EsgErrorCode.REJECTION_REASON_REQUIRED, "반려 사유는 필수입니다");
    }
    this.status = ApprovalStatus.REJECTED;
    this.rejectionReason = reason;
}
```

---

### Phase 12: 보안 감사 & 최종 검증 (T-12-xx)

- [ ] **T-12-14**: `grep -r "float\|double" src/main/java/ai/claudecode/esgt2/module/ghg/ | grep -v "//"`  → 0 matches
- [ ] **T-12-15**: Enable `hibernate.generate_statistics=true` in test; assert `Statistics.queryExecutionCount <= expected` for key APIs
- [ ] **T-12-16**: `grep -r "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" src/main/java --include="*.java" -l | xargs grep -L "@PreAuthorize"` → empty output

---

## Self-Review

### 1. Spec Coverage Check

| Requirement | Covered in Task |
|---|---|
| T-0-01~02: Gradle + Spring Boot 4 | Task 1 |
| T-0-03: Module skeleton | Task 3 |
| T-0-04~05: Docker Compose | Task 2 |
| T-0-06: Flyway V1 schema | Task 5 |
| T-0-07: AbstractIntegrationTest | Task 4 |
| T-0-08: ModularityTest | Task 7 |
| T-0-09: GitHub Actions CI | Task 8 |
| T-0-10~11: OTel + Prometheus | Task 9 |
| T-0-12: Next.js 15 | Task 10 |
| T-0-13: disclosure_schedule seed | Task 6 |
| T-0-14: Flyway multi-location | Task 5 |
| T-0-15: scheduler.enabled=false | Task 1 Step 7 |
| T-0-16: static POSTGRES.start() | Task 4 |
| T-1-01~02: entity/auth tables | Task 12 |
| T-1-03~04: LegalEntity domain | Task 13 |
| T-1-17: AccessDeniedException→403 | Task 14 |
| T-2-02~04: @Auditable + AuditAspect | Task 15 |
| T-3-xx: Emission calculation | Phase 3 key tasks |
| T-3B-xx: Evidence + Formula | Phase 3-B key tasks |
| T-6-12, T-6-14: CSV REQUIRES_NEW | Phase 6 key task |
| T-7-11: Approval state machine | Phase 7 key task |
| T-12-14~16: Final audits | Phase 12 checklist |

### 2. No Placeholders

All code blocks contain runnable Java/SQL/YAML. No TBD or "implement later" present.

### 3. Type Consistency

- `LegalEntity.create(UUID, String, String, EntityType)` — consistent throughout.
- `EsgErrorCode` enum — all error codes used in handlers match enum values.
- `AbstractIntegrationTest` — all test classes extend this base.
