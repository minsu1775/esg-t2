# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **선행 프로젝트**: `../esg-t1` — MVP Phase 0~12 완성본. esg-t2는 esg-t1의 아키텍처 교훈과 규제 업데이트(KSSB 1/2, 2026-02-26)를 반영한 2세대 프로젝트다.  
> **문서 읽는 순서**: `docs/regulatory.md` → `docs/prd.md` → `docs/spec.md` → `docs/plan.md` → `docs/task.md` → 본 파일

---

## 프로젝트 개요

ESG 공시지원 시스템 2세대. KSSB 1/2(한국판 IFRS S1/S2), Scope 3, 다법인 연결, 외부 검증 모드를 MVP에 포함한 엔터프라이즈 공시 플랫폼.

- **Group**: `ai.claudecode` | **Artifact**: `esgt2` | **Base package**: `ai.claudecode.esgt2`
- **초기 생성**: Spring Initializr (https://start.spring.io) 필수. 수동 `build.gradle.kts` 작성 금지.

---

## 기술 스택

| 구성요소 | 버전/선택 |
|---|---|
| Java | 25 LTS (Virtual Threads, Record Patterns) |
| Spring Boot | 4.0.x (Spring Framework 7) |
| Spring Modulith | `@ApplicationModule` + `ModularityTest` 경계 강제 |
| JPA / Hibernate | Jakarta Persistence 3.2 / Hibernate 7 |
| DB (운영·테스트) | PostgreSQL 18 (TestContainers) |
| DB (로컬 보조) | H2 |
| Build | Gradle Kotlin DSL |
| API 문서 | springdoc-openapi (OpenAPI 3.1) |
| 관측성 | OpenTelemetry (Spring Boot 4 내장) |
| Frontend | Next.js (latest) + TypeScript strict + Tailwind 4.x |

> **UI 개발 게이트**: UI Phase 시작 전 반드시 사용자 승인 후 착수.

---

## 명령어

```bash
# 백엔드
./gradlew build
./gradlew bootRun
./gradlew test
./gradlew test --tests "ai.claudecode.esgt2.domain.DataPointTest"
./gradlew test --tests "ai.claudecode.esgt2.domain.DataPointTest.정정시_새_버전이_추가된다"
./gradlew test --tests "*ModularityTest"
./gradlew generateOpenApiDocs
./gradlew spotlessApply

# 프론트엔드
cd frontend && npm run dev
cd frontend && npm run typecheck
cd frontend && npm run build
```

---

## 아키텍처 개요

```
ai.claudecode.esgt2
├── ghg/     # GHG 배출량 계산 (Scope 1·2·3, 연결 집계)
├── entity/  # 법인·테넌트 관리
├── audit/   # AuditLog + Hash Chain (@Auditable AOP)
├── vw/      # Verification Workspace (외부 검증인)
├── rpt/     # 보고서 생성 (KSSB 2, PDF)
├── supply/  # 공급업체 포털 (Scope 3 Cat.1)
└── shared/  # 공통 Value Object, Event, Exception
```

각 모듈: `api/` (공개) · `domain/` (순수 Java) · `infra/` (JPA·외부) · `internal/` (비공개)  
모듈 간 동기: `api/` 인터페이스만 허용 | 비동기: `ApplicationEventPublisher` 사용

---

## 코딩 규칙

세부 규칙은 `.claude/rules/` 파일 참조. 경로 조건에 따라 자동 로드된다.

| 파일 | 내용 | 로드 조건 |
|---|---|---|
| `01-domain-architecture.md` | Domain≠Entity, Default*, 불변성, 검증 우선, Lombok, SQL 예약어 | 항상 |
| `02-testing.md` | TDD, TestContainers, Mock DB 금지, 테스트 격리 | `src/test/**` |
| `03-security.md` | RBAC, RLS, 크로스테넌트 방어, JWT, Webhook 서명 | 항상 |
| `04-api-design.md` | OpenAPI 어노테이션, GlobalExceptionHandler 핸들러, REST 규칙 | `**/controller/**` |
| `05-async-concurrency.md` | @Async+@Transactional 분리, PESSIMISTIC_WRITE, Outbox | `**/service/**` |
| `06-emission-calculation.md` | BigDecimal 전용, factorAt 재현성, Scope 3 집계 | `**/ghg/**` |
| `07-formula-dsl.md` | DoS 방어 상수, test_cases 게이트, YAML 로더 멱등성 | `**/formula/**` |
| `08-persistence.md` | Flyway 멀티 로케이션, Append-only Repository, N+1 방지 | `**/infra/**` |
| `09-scheduler.md` | zone=Asia/Seoul, @ConditionalOnProperty 격리 | `**/*Scheduler*.java` |
| `10-evidence-files.md` | DigestInputStream, 경로 순회 방어, SHA-256 | `**/evidence/**` |
| `11-modulith-events.md` | 모듈 경계, @Auditable 적용 범위, @ApplicationModuleListener | `**/module/**` |

---

## 개발 워크플로우

1. `docs/task.md` Phase 체크리스트 — 완료 즉시 체크
2. Phase 종료 시 `docs/code-review.md` 리뷰 기록
3. 이슈 발견 → `docs/fix.md` 등록 → TDD로 해결
4. 학습 인사이트 → `docs/insight.md` 누적

---

## 문서 구조

```
docs/
├── regulatory.md   # ESG 규제 레퍼런스 (KSSB, GHG Protocol)
├── prd.md          # 제품 요구사항
├── spec.md         # 기술 명세 (ERD, 모듈 설계, 보안 모델)
├── plan.md         # 실행 계획 Phase 0~12
├── task.md         # Phase별 태스크 체크리스트
├── code-review.md  # 코드 리뷰 체크리스트 & 이력
├── fix.md          # 이슈 트래킹
├── insight.md      # 학습 인사이트 (esg-t1 교훈 L-0-01~L-0-16)
├── openapi.yml     # 빌드 자동 생성
└── adr/            # ADR-001~005
```
