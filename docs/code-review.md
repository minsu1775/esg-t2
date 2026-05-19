# ESG 공시지원 시스템 esg-t2 — 코드 리뷰 체크리스트 & 이력 (Code Review)

> **목적**: 코드 리뷰에서 반복적으로 확인해야 할 항목을 정의하고, 주요 리뷰 결과를 기록한다.  
> **갱신**: 새로운 공통 이슈 발견 시 체크리스트에 항목 추가

---

## 1. 필수 체크리스트 (PR 생성 전 자가 점검)

### 1.1 도메인 설계

- [ ] 서비스에서 `JpaEntity.builder()...build()` 직접 호출 없음 (도메인 팩토리만 허용)
- [ ] 도메인 객체는 불변(record 또는 final 필드) 또는 명시적 변경 메서드만 존재
- [ ] `DomainObject.create(cmd)` 팩토리 메서드에 유효성 검사 포함

### 1.2 @Auditable AOP

- [ ] 데이터 변경 메서드(create/update/delete/approve)에 `@Auditable(action = "...")` 부착
- [ ] @Auditable 통합 테스트: `AuditLog` 1건 이상 생성됨 검증 포함

### 1.3 테스트

- [ ] 서비스 레이어 통합 테스트에서 Mock DB 사용 없음 (Testcontainers PostgreSQL 사용)
- [ ] 도메인 단위 테스트에서 경계값(null, 빈값, 최대값) 케이스 포함
- [ ] TDD 순서 준수: `test:` 커밋 먼저, `feat:` 커밋 나중

### 1.4 Spring Modulith 경계

- [ ] 다른 모듈의 `internal` 패키지 직접 참조 없음
- [ ] 모듈 간 비동기 통신 → `ApplicationEventPublisher` 사용
- [ ] `ModularityTest` 로컬에서 통과 확인

### 1.5 보안

- [ ] 사용자 입력 → SQL 쿼리에 PreparedStatement 사용 (문자열 연결 금지)
- [ ] API 응답에 비밀번호·토큰·개인식별정보 노출 없음
- [ ] 역할 기반 `@PreAuthorize` 적용 (인가 없는 엔드포인트 없음)
- [ ] 크로스 테넌트 데이터 접근 방지: RLS 정책 + 애플리케이션 레벨 이중 방어

### 1.6 YAML 로더 (배출계수)

- [ ] 파일 레벨 스킵 로직 없음 (`already_processed` 파일 체크 금지)
- [ ] `ON CONFLICT DO UPDATE` 항목 레벨 upsert 사용

### 1.7 Hash Chain

- [ ] AuditLog 저장 시 PESSIMISTIC_WRITE 락 사용
- [ ] `synchronized` + `@Transactional` 조합 없음

### 1.8 증빙 파일 (Evidence)

- [ ] 파일 업로드 시 `DigestInputStream` 사용 (I/O 1회 = 업로드 + SHA-256 동시)
- [ ] `evidence_files`에 저장 전 SHA-256 무결성 검증 완료
- [ ] Object Storage URI는 DB에 저장, 파일 자체는 DB에 저장하지 않음
- [ ] 활동 데이터 삭제 금지 — 증빙과 연결된 데이터는 비활성화 처리

### 1.9 정정·재공시

- [ ] `activity_data` 정정 시 `reason_code` 필수 — 빈 문자열 불가
- [ ] 정정 메서드는 새 버전 INSERT만 수행 (UPDATE/DELETE 없음)
- [ ] 정정 후 재산출 이벤트 발행 확인

### 1.10 단위 변환

- [ ] 활동 데이터 저장 시 원 단위(`unit`)와 변환 단위(`standard_value`, `standard_unit`) 모두 저장
- [ ] `UnitConverter.convert()` 단일 메서드 경유 — 직접 계산 금지

### 1.11 코드 품질

- [ ] 메서드 길이 ≤ 30행 (초과 시 분리 검토)
- [ ] 의미 없는 주석(WHAT) 없음; WHY가 자명하지 않을 때만 주석
- [ ] `Default*` 네이밍: 인터페이스 구현체의 기본 구현에 사용
- [ ] 컬럼명에 SQL 예약어 사용 없음 (`year` → `reporting_year`, `value` → `data_value`)

---

## 2. 리뷰 이력

> 형식: `YYYY-MM-DD | 범위 | 핵심 발견 사항`

### 2026-05-19 | Phase 0 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| 모듈 패키지 구조 | ✅ | `@ApplicationModule` 6개 모듈, `package-info.java` 구조 정상 |
| `ModularityTest` | ✅ PASS | 모듈 경계 위반 없음 |
| CI 파이프라인 | ✅ | `.github/workflows/ci.yml` — Java 25, TESTCONTAINERS_RYUK_DISABLED |
| Flyway 멀티 로케이션 | ✅ | `db/migration` + `db/migration-pg` 분리 |
| V1 스키마 | ✅ | `event_publication.status` 컬럼 추가 (Spring Modulith 2.0.0 필수) |
| Spring Boot 4 호환성 | ✅ | `TestRestTemplate` 제거 → `MockMvcBuilders` 교체 완료 |
| 테스트 결과 | ✅ **9 tests, 0 failures** | ActuatorEndpointTest, FlywayMigrationTest, ModularityTest, AbstractIntegrationTestTest, Esgt2ApplicationTest |
| Redis 비활성화 전략 | ✅ | `management.health.redis.enabled=false` + Redis AutoConfig exclude |
| Docker TCP 설정 | ✅ | `DOCKER_HOST=tcp://localhost:2375`, `api.version=1.40` |

**개선 필요 사항 (Phase 1 착수 전)**:
- `event_publication` 테이블 H2 shutdown WARN — 허용 가능한 경고 (Spring Modulith destroy callback 순서 문제). 테스트 실패 아님.
- T-0-12 (Next.js) — UI 개발 게이트 대기 중 (사용자 승인 필요)

### 2026-05-19 | Phase 1 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| Domain≠Entity 원칙 | ✅ | LegalEntity, EntityRelationship 순수 Java record. JPA entity 분리 완료 |
| `DomainObject.create(cmd)` 팩토리 | ✅ | 서비스에서 domain factory 경유, JpaEntity.builder() 직접 호출 없음 |
| 검증 우선 원칙 | ✅ | `create()` 호출 전 파라미터 검증 완료 (ValidationFirstPrincipleTest 3건) |
| EntityRelationshipGraph DAG 사이클 탐지 | ✅ | DFS + inStack 방식, EsgException(VALIDATION_FAILED) 발생 |
| JWT 인증 흐름 | ✅ | NimbusJwtEncoder/Decoder, 15분/7일 토큰, Redis 블랙리스트 |
| TenantContextInterceptor set_config() | ✅ | SQL 파라미터 바인딩 사용, 문자열 연결 없음 (L-P1-03 반영) |
| @NamedInterface 선언 | ✅ | shared/exception, security, tenant, web 4개 패키지 선언 (L-P1-01 반영) |
| SecurityConfig vs @PreAuthorize | ✅ | 역할 matchers 제거, 전부 @PreAuthorize로 통일 (L-P1-02 반영) |
| ModularityTest | ✅ PASS | 모듈 경계 위반 없음 |
| 전체 테스트 | ✅ **47 tests, 0 failures** | Phase 0(9) + Phase 1(38) |

**Phase 1 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `JwtTokenProvider.secretKey()` 매 요청마다 SecretKey 재생성 | P2 | explicit constructor로 `secretKey` + `jwtDecoder` 캐싱 |
| `JwtAuthenticationFilter` catch(Exception) 광범위 처리 | P2 | `JwtException` 별도 catch → 나머지 warn 로그 |
| `DefaultAuthService.refresh()` `@Transactional` 누락 | P2 | `@Transactional(readOnly = true)` 추가 |
| `DefaultEntityManagementService` 와일드카드 임포트 | P3 | 명시적 임포트로 교체 |
| DTO 전체 `@Schema` 어노테이션 미적용 | P3 | `CreateEntityRequest`, `EntityResponse` 등 8개 DTO에 `@Schema` 추가 |
| `EntityControllerSecurityTest` TENANT_ADMIN 테스트 NPE | P2 | `@WithMockJwtUser` 테스트 어노테이션 생성, 테스트 교체 |

**잔존 기술 부채 (Phase 2 이후 해소 예정)**:

| 항목 | 우선순위 | 내용 |
|---|---|---|
| `DefaultAuthService.blacklist()` Redis key | P3 | 전체 JWT 토큰 문자열을 key로 사용 중 (300~500자). JTI claim 추가 후 JTI를 key로 교체 필요 |
| `application.yml` JWT secret | P2 | 개발용 하드코딩 secret. 운영 환경은 `JWT_SECRET` 환경변수 오버라이드 필수 |
| `@Auditable` AOP 미적용 | P1 | `entity.create`, `setRelationship` 메서드에 Phase 2 구현 후 부착 (Phase 2에서 해소) |

### 2026-05-19 | Phase 2 완료 리뷰

| 항목 | 결과 | 비고 |
|---|---|---|
| `@Auditable` AOP (AuditAspect) | ✅ | `@Aspect` + `@Around`, Outbox INSERT 정상 |
| Hash Chain — PESSIMISTIC_WRITE 락 | ✅ | `findFirstByTenantIdOrderByIdDesc()` `@Lock(PESSIMISTIC_WRITE)` |
| Canonical JSON 단일 직렬화 | ✅ | `HashChainCalculator.canonicalPayload()` — TreeMap 키 정렬 보장 |
| `@ConditionalOnProperty(scheduler.enabled)` | ✅ | 두 스케줄러 모두 조건부 |
| Outbox Poller — `fixedDelay` (zone 미사용) | ✅ | 09-scheduler.md 규칙 준수 |
| 무결성 검증 스케줄러 — `zone=Asia/Seoul` | ✅ | 09-scheduler.md 규칙 준수 |
| DB 인덱스 | ✅ | `idx_audit_logs_tenant`, `idx_outbox_status` 생성 |
| 통합 테스트 4건 | ✅ PASS | Outbox 생성, AuditLog 생성, Hash Chain 연결, 비감사 메서드 확인 |

**Phase 2 리뷰에서 발견하여 즉시 수정한 항목**:

| 발견 사항 | 심각도 | 수정 내용 |
|---|---|---|
| `AuditLogRepository extends JpaRepository` — delete 메서드 노출 | **P0** | `Repository<T,ID>`로 교체, 명시적 메서드만 선언 (08-persistence.md append-only 원칙) |
| `audit_logs` RLS 정책 + REVOKE 누락 | **P0** | `db/migration-pg/V5__audit_rls.sql` 생성 — `ENABLE ROW LEVEL SECURITY` + `REVOKE UPDATE, DELETE` |
| `AuditAspect` try-catch로 Outbox 저장 실패 삼킴 | **P0** | try-catch 제거 — Outbox 실패 시 @Transactional 롤백 보장 |
| `AuditIntegrityScheduler` — `@Transactional` 직접 부착 | **P1** | `AuditIntegrityService` 별도 빈 생성, 스케줄러는 위임만 수행 (09-scheduler.md 규칙) |
| `AuditIntegrityScheduler` — `findAll()` 전체 로드 | **P2** | `findDistinctTenantIds()` JPQL 쿼리로 교체 (성능) |
| `OutboxEventJpaEntity` — `@Setter` on status | **P2** | `@Setter` 제거 — `markProcessed()`, `markFailed()` 명시적 상태 전이 메서드만 허용 (01-domain-architecture.md) |
| `AuditableIntegrationTest.cleanup()` — `auditLogRepository.deleteAll()` 컴파일 오류 | **필수** | `JdbcTemplate.execute("DELETE FROM audit_logs")` 교체 |

**잔존 기술 부채 (Phase 3 이후 해소 예정)**:

| 항목 | 우선순위 | 내용 |
|---|---|---|
| `OutboxProcessingService.processNow()` 단일 트랜잭션 | P2 | 모든 Outbox 이벤트를 하나의 트랜잭션으로 처리. 5번째 이벤트 실패 시 DB 상태가 오염될 수 있음. `REQUIRES_NEW` 패턴 적용 권장 (05-async-concurrency.md CSV 패턴과 동일) |
| `AuditAspect` vs `@Transactional` 어드바이저 순서 비결정적 | P2 | 명시적 `@Order` 없이 런타임 순서에 의존. `@EnableTransactionManagement(order=0)` + `@Order(1)` on `AuditAspect`로 명시화 권장 |
| `DefaultEntityManagementService` — `LegalEntityJpaEntity.builder()` 직접 호출 | P1 | 서비스에서 JPA Entity 직접 생성 (01-domain-architecture.md 위반). Phase 3 착수 전 Mapper 도입 필요 |
| `OutboxEventJpaEntity.markFailed()` — 에러 메시지 미기록 | P3 | 09-scheduler.md "에러 메시지 기록" 요구사항. `error_message` 컬럼 추가 + `markFailed(String msg)` 오버로드 |

---

## 3. 공통 이슈 트래킹

> 같은 이슈가 3회 이상 반복되면 체크리스트에 항목 추가

| 이슈 패턴 | 발생 횟수 | 대응 |
|---|---|---|
| 테스트에서 `@WithMockUser` 사용 시 typed `@AuthenticationPrincipal` NPE | 1 | `WithMockJwtUser` 커스텀 어노테이션 패턴 수립 |

---

## 4. Phase별 리뷰 포커스

| Phase | 중점 리뷰 항목 |
|---|---|
| Phase 0 | 모듈 패키지 구조 (중간 `module/` 디렉터리 없음), CI 파이프라인 정확성, Flyway 체크섬 불변 확인 |
| Phase 1 | Domain≠Entity 원칙, RLS 정책 적용, TenantContextInterceptor `SET LOCAL` 검증, 크로스 테넌트 이중 방어 |
| Phase 2 | @Auditable AOP 커버리지, Hash Chain PESSIMISTIC_WRITE, Canonical JSON 단일 직렬화 경로 |
| Phase 3 | YAML 로더 멱등성, 배출계수 계산 `BigDecimal` 전용(float/double 금지), `reporting_year` SQL 예약어 방지, `factorAt` 재현성 테스트 |
| Phase 4 | 연결 집계 이중 계상 제거 알고리즘 |
| Phase 5 | Scope 3 95% 임계값 계산 로직, 데이터 품질 점수 |
| Phase 6 | 공급업체 데이터 격리, Webhook 시그니처 검증 |
| Phase 7 | KSSB 2 항목 완전성 (미구현 항목 없음), YoY 계산 |
| Phase 8 | 스냅샷 불변성, VERIFIER RLS 격리 |
| Phase 9~11 | XSS 방어, RBAC 메뉴 분기, 접근성 |
| Phase 12 | 성능 벤치마크, OWASP 결과 해소 |
