---
name: testing
description: 테스트 정책, TDD 사이클, TestContainers 초기화, Mock DB 금지
paths:
  - "src/test/**"
  - "**/*Test.java"
  - "**/*Tests.java"
  - "**/*Spec.java"
---

# 테스트 정책

## 테스트 계층 및 도구

| 대상 | 종류 | 도구 |
|---|---|---|
| Domain, Value Object, 순수 로직 | Unit | JUnit 5 + AssertJ. **JPA 사용 금지.** |
| Service (`DefaultXxxService`) | 통합 | `@SpringBootTest` + TestContainers PostgreSQL. **Mock 라이브러리 금지.** |
| Repository | 통합 | `@DataJpaTest` 또는 `@SpringBootTest` |
| Controller | 통합 | `@SpringBootTest` + `MockMvc` |
| Spring Modulith 경계 | 아키텍처 | `ModularityTest` |

## TDD 사이클 (필수)

1. **Red** — 실패하는 테스트 먼저 작성 (`test:` 커밋)
2. **Green** — 통과하는 최소 구현 (`feat:` 커밋)
3. **Refactor** — 통과 유지하며 개선 (`refactor:` 커밋)

커밋 prefix 순서: `test:` → `feat:` → `refactor:`

## 테스트 메서드명

**한국어 + underscore**로 시나리오를 그대로 표현.

```java
@Test
void 배출계수_미존재_시_예외가_발생한다() { ... }

@Test
void 정정_후_이전_버전이_보존된다() { ... }
```

## 테스트 격리

```java
@Transactional
@Sql(scripts = "/cleanup.sql", executionPhase = AFTER_TEST_METHOD)
class EmissionServiceIntegrationTest { ... }
// cleanup.sql: FK 순서대로 DELETE
```

## TestContainers 초기화 순서 (esg-t1 교훈)

`@DynamicPropertySource`보다 컨테이너 `start()`가 반드시 먼저 실행되어야 한다.

```java
static final PostgreSQLContainer<?> POSTGRES =
    new PostgreSQLContainer<>("postgres:18");

static {
    POSTGRES.start();   // @DynamicPropertySource 전에 실행
}

@DynamicPropertySource
static void overrideProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
}
```

## @TestConfiguration vs @Configuration

테스트 전용 빈은 반드시 `@TestConfiguration`. `@Configuration`은 프로덕션 빈만 (esg-t1 L-0-05).

## Mock DB 절대 금지

서비스 레이어 통합 테스트에서 H2 in-memory나 Mock Repository 사용 금지.
반드시 Testcontainers PostgreSQL 사용 → 운영 환경과 동일한 RLS·트리거 동작 보장.

## @Auditable 검증

데이터 변경 통합 테스트에서 `AuditLog` 1건 이상 생성됨을 반드시 assert.

```java
@Test
void 활동데이터_생성_시_감사로그가_기록된다() {
    service.create(cmd);
    assertThat(auditLogRepository.findAll()).hasSize(1);
}
```

## Scheduler 테스트 격리

`application-test.yml`에 `scheduler.enabled: false` 설정 필수.
통합 테스트 중 스케줄러 자동 실행으로 테스트 데이터가 오염되지 않도록 방지.

## N+1 쿼리 검증

통합 테스트에서 Hibernate statistics 활성화로 쿼리 수 검증.

```java
@Test
void 목록_조회_시_N1_쿼리가_발생하지_않는다() {
    // given: 10개 법인 + 관계 데이터
    // when: 연결 집계 조회
    // then
    assertThat(statistics.getEntityLoadCount()).isLessThanOrEqualTo(3);
}
```
