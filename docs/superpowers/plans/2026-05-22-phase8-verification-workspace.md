# Phase 8: 외부 검증 워크스페이스 (vw 모듈) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** vw 모듈을 구현하여 APPROVED 보고서를 SHA-256 불변 스냅샷으로 동결하고, 외부 검증인(VERIFIER)이 자신에게 지정된 스냅샷에만 접근·코멘트·서명할 수 있는 격리된 검증 워크스페이스를 제공한다.

**Architecture:** VerificationSnapshot은 `disclosure_reports.content` JSON을 복사·직렬화하여 SHA-256 해시로 무결성을 봉인한다. DB 레벨 트리거(migration-pg)와 도메인 레벨 append-only Repository로 이중 불변성을 보장한다. VERIFIER 역할은 JWT claim `verifier_snapshot_id`로 단일 스냅샷에 격리되며, `TenantContextInterceptor`가 `app.verifier_snapshot_id` 세션 변수를 설정해 PostgreSQL RLS가 DB 레벨에서도 강제한다. 서명은 별도 `verification_signatures` 테이블에 기록하여 스냅샷 불변성을 완전히 유지한다.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security SpEL (`@snapshotSecurity.canAccess()`), PostgreSQL 18 (BEFORE 트리거 + RLS), TestContainers PostgreSQL, SHA-256 (`java.security.MessageDigest`), Jackson ObjectMapper, Apache PDFBox (기존)

---

## 파일 구조

### 신규 생성
| 파일 | 역할 |
|---|---|
| `src/main/resources/db/migration/V23__verification_tables.sql` | DDL: verification_snapshots, verification_comments, verification_signatures |
| `src/main/resources/db/migration-pg/V23__vw_pg_rls.sql` | PG 전용: UPDATE/DELETE 차단 트리거, REVOKE, RLS 정책 |
| `src/main/java/.../vw/domain/VerificationSnapshot.java` | 불변 도메인 record (SHA-256 해시 생성 포함) |
| `src/main/java/.../vw/domain/VerificationComment.java` | 코멘트 도메인 record |
| `src/main/java/.../vw/domain/VerificationSignature.java` | 서명 도메인 record |
| `src/main/java/.../vw/infra/VerificationSnapshotJpaEntity.java` | JPA Entity (JSONB 매핑) |
| `src/main/java/.../vw/infra/VerificationSnapshotRepository.java` | extends Repository (append-only) |
| `src/main/java/.../vw/infra/VerificationCommentJpaEntity.java` | JPA Entity |
| `src/main/java/.../vw/infra/VerificationCommentRepository.java` | extends Repository (append-only) |
| `src/main/java/.../vw/infra/VerificationSignatureJpaEntity.java` | JPA Entity |
| `src/main/java/.../vw/infra/VerificationSignatureRepository.java` | extends Repository (append-only) |
| `src/main/java/.../vw/security/SnapshotSecurityService.java` | SpEL 보안 빈 `@snapshotSecurity` |
| `src/main/java/.../vw/api/SnapshotService.java` | 서비스 공개 인터페이스 (VwApi.java 대체) |
| `src/main/java/.../vw/api/SnapshotResponse.java` | 스냅샷 응답 DTO |
| `src/main/java/.../vw/api/CommentResponse.java` | 코멘트 응답 DTO |
| `src/main/java/.../vw/internal/DefaultSnapshotService.java` | 서비스 구현체 |
| `src/main/java/.../vw/api/VwController.java` | REST 컨트롤러 |
| `src/test/java/.../vw/SnapshotIntegrationTest.java` | 통합 테스트 T-8-03~05 포함 |

### 수정
| 파일 | 변경 내용 |
|---|---|
| `src/main/java/.../shared/exception/EsgErrorCode.java` | REPORT_NOT_APPROVED, SNAPSHOT_NOT_FOUND 추가 |
| `src/main/java/.../shared/security/JwtAuthentication.java` | `snapshotId` 필드 + 생성자 추가 (VERIFIER 전용) |
| `src/main/java/.../shared/security/JwtAuthenticationFilter.java` | `verifier_snapshot_id` JWT claim 파싱 |
| `src/main/java/.../shared/tenant/TenantContextInterceptor.java` | `app.verifier_snapshot_id` SET LOCAL 추가 |
| `src/main/java/.../vw/api/VwApi.java` | placeholder 제거, SnapshotService 참조 주석으로 교체 |
| `docs/task.md` | Phase 8 T-8-01~10 완료 체크 |

> **패키지 접두사**: `ai.claudecode.esgt2` — 이하 `...`로 단축 표기

---

### Task 1: DB 마이그레이션 (T-8-01)

**Files:**
- Create: `src/main/resources/db/migration/V23__verification_tables.sql`
- Create: `src/main/resources/db/migration-pg/V23__vw_pg_rls.sql`

- [ ] **Step 1: V23 공통 DDL 작성**

```sql
-- src/main/resources/db/migration/V23__verification_tables.sql

-- 검증 스냅샷: APPROVED 보고서의 불변 복사본
CREATE TABLE verification_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL REFERENCES disclosure_reports(id),
    snapshot_hash   VARCHAR(64) NOT NULL,  -- SHA-256 hex (64자)
    snapshot_data   JSONB NOT NULL,        -- 보고서 내용 불변 복사
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    frozen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_snapshots_tenant ON verification_snapshots(tenant_id);
CREATE INDEX idx_verification_snapshots_report ON verification_snapshots(report_id);

-- 검증 코멘트: VERIFIER가 작성하는 의견
CREATE TABLE verification_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES verification_snapshots(id),
    tenant_id   UUID NOT NULL,
    author_id   UUID NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_comments_snapshot ON verification_comments(snapshot_id);

-- 검증 서명: VERIFIER의 최종 서명 (스냅샷당 1건 unique)
CREATE TABLE verification_signatures (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL UNIQUE REFERENCES verification_snapshots(id),
    tenant_id   UUID NOT NULL,
    signed_by   UUID NOT NULL,
    signed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sign_note   TEXT
);

CREATE INDEX idx_verification_signatures_snapshot ON verification_signatures(snapshot_id);
```

- [ ] **Step 2: V23 PostgreSQL 전용 DDL 작성 (RLS + 불변성 트리거)**

```sql
-- src/main/resources/db/migration-pg/V23__vw_pg_rls.sql

-- ============================================================
-- 스냅샷 불변성 트리거: UPDATE/DELETE 차단
-- (CREATE RULE DO INSTEAD NOTHING 방식은 PG 레거시 기능으로
--  오류 없이 조용히 무시됨 → 트리거 방식 필수)
-- ============================================================
CREATE OR REPLACE FUNCTION prevent_snapshot_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'verification_snapshots는 불변입니다. UPDATE/DELETE 금지.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER snapshot_immutable
    BEFORE UPDATE OR DELETE ON verification_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_snapshot_modification();

-- DB 권한 박탈 (app_user가 직접 UPDATE/DELETE 시도 불가)
REVOKE UPDATE, DELETE ON verification_snapshots FROM app_user;

-- ============================================================
-- verification_snapshots RLS
-- ============================================================
ALTER TABLE verification_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_snapshots FORCE ROW LEVEL SECURITY;

-- 테넌트 격리 (기본)
CREATE POLICY tenant_isolation_snapshots ON verification_snapshots
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);

-- VERIFIER 추가 격리: 지정 snapshot_id만
CREATE POLICY verifier_snapshot_isolation ON verification_snapshots
    FOR SELECT TO app_user
    USING (
        current_setting('app.verifier_snapshot_id', true) IS NULL
        OR id = current_setting('app.verifier_snapshot_id', true)::UUID
    );

-- ============================================================
-- verification_comments RLS
-- ============================================================
ALTER TABLE verification_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_comments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_comments ON verification_comments
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);

-- ============================================================
-- verification_signatures RLS
-- ============================================================
ALTER TABLE verification_signatures ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_signatures FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_signatures ON verification_signatures
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);
```

- [ ] **Step 3: 마이그레이션 적용 확인**

```bash
./gradlew test --tests "ai.claudecode.esgt2.EsgT2ApplicationTests"
```

Expected: BUILD SUCCESS (기존 테스트 통과, V23 마이그레이션 적용)

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/db/migration/V23__verification_tables.sql
git add src/main/resources/db/migration-pg/V23__vw_pg_rls.sql
git commit -m "feat: T-8-01 V23 verification_snapshots/comments/signatures DDL + PG RLS"
```

---

### Task 2: Red 테스트 작성 (T-8-03, T-8-04, T-8-05)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/vw/SnapshotIntegrationTest.java`

- [ ] **Step 1: 테스트 뼈대 + T-8-05 (미승인 보고서 게이트)**

```java
package ai.claudecode.esgt2.vw;

import ai.claudecode.esgt2.rpt.api.CreateReportRequest;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import ai.claudecode.esgt2.vw.api.SnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * 외부 검증 워크스페이스 통합 테스트 (T-8-03~10).
 */
class SnapshotIntegrationTest extends AbstractIntegrationTest {

    @Autowired SnapshotService snapshotService;
    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000097");
    private static final UUID ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000098");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "DELETE FROM verification_signatures WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM verification_comments WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM verification_snapshots WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM disclosure_reports WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM emission_records WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");
        jdbcTemplate.execute(
            "DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000097'");

        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000097','VW97','검증테스트','KR') " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.execute(
            "INSERT INTO legal_entities (id, tenant_id, name, country_code, entity_type) " +
            "VALUES ('00000000-0000-0000-0000-000000000098','00000000-0000-0000-0000-000000000097'," +
            "'검증법인','KR','SUBSIDIARY') ON CONFLICT DO NOTHING");

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER", "TENANT_ADMIN")));
    }

    // T-8-05: 미승인 보고서 → 스냅샷 생성 시도 → EsgException
    @Test
    void 미승인_보고서로_스냅샷_생성_시도_시_예외() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        // DRAFT 상태 — 아직 승인 안 됨

        assertThatThrownBy(() ->
            snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id()))
            .isInstanceOf(EsgException.class);
    }

    // T-8-03: 스냅샷 불변성 — Repository는 append-only (delete 메서드 미노출)
    @Test
    void 스냅샷_Repository는_append_only_인터페이스이다() {
        // Reflection으로 Repository 메서드 목록 확인
        var repoClass = ai.claudecode.esgt2.vw.infra.VerificationSnapshotRepository.class;
        var methods = Arrays.stream(repoClass.getMethods())
            .map(m -> m.getName())
            .toList();
        assertThat(methods).doesNotContain(
            "delete", "deleteById", "deleteAll",
            "deleteAllById", "deleteAllInBatch");
    }

    // T-8-03: 스냅샷 생성 후 내용 불변 확인 (hash 일치)
    @Test
    void 스냅샷_생성_후_해시_유지() {
        // APPROVED 보고서 생성
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());

        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        assertThat(snapshot.id()).isNotNull();
        assertThat(snapshot.snapshotHash()).hasSize(64);  // SHA-256 hex = 64자
        assertThat(snapshot.reportId()).isEqualTo(report.id());

        // 동일 reportId로 다시 조회해도 hash가 동일해야 함
        var snapshot2 = snapshotService.getSnapshot(TENANT_ID, snapshot.id());
        assertThat(snapshot2.snapshotHash()).isEqualTo(snapshot.snapshotHash());
    }

    // T-8-04: VERIFIER → 지정 스냅샷 외 접근 → AccessDeniedException
    @Test
    void VERIFIER_다른_스냅샷_접근_시_AccessDenied() {
        // APPROVED 보고서 + 스냅샷 생성 (ESG_MANAGER로)
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        // VERIFIER 인증: 다른 snapshot ID로 설정 (접근 불가)
        UUID otherSnapshotId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, null, otherSnapshotId,
                List.of("VERIFIER")));

        assertThatThrownBy(() ->
            snapshotService.getSnapshot(TENANT_ID, snapshot.id()))
            .isInstanceOf(AccessDeniedException.class);
    }

    // T-8-04 보완: VERIFIER → 지정된 스냅샷은 접근 허용
    @Test
    void VERIFIER_지정된_스냅샷은_접근_허용() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        // VERIFIER 인증: 정확한 snapshot ID로 설정
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, null, snapshot.id(),
                List.of("VERIFIER")));

        assertThatCode(() ->
            snapshotService.getSnapshot(TENANT_ID, snapshot.id()))
            .doesNotThrowAnyException();
    }

    // T-8-09: 코멘트 작성 + 조회
    @Test
    void 코멘트_작성_후_조회() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        snapshotService.addComment(TENANT_ID, ACTOR_ID, snapshot.id(), "Scope 1 데이터 확인 요청");

        var comments = snapshotService.listComments(TENANT_ID, snapshot.id());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).body()).isEqualTo("Scope 1 데이터 확인 요청");
    }

    // T-8-10: 서명 완료
    @Test
    void 검증_완료_서명() {
        var report = reportService.createReport(TENANT_ID, ACTOR_ID,
            new CreateReportRequest(ENTITY_ID, 2025, "KSSB2"));
        reportService.submitReport(TENANT_ID, ACTOR_ID, report.id());
        reportService.approveReport(TENANT_ID, ACTOR_ID, report.id());
        var snapshot = snapshotService.createSnapshot(TENANT_ID, ACTOR_ID, report.id());

        assertThat(snapshotService.isSigned(TENANT_ID, snapshot.id())).isFalse();

        snapshotService.signSnapshot(TENANT_ID, ACTOR_ID, snapshot.id(), "검증 완료 확인");

        assertThat(snapshotService.isSigned(TENANT_ID, snapshot.id())).isTrue();
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 오류 확인 (Red)**

```bash
./gradlew test --tests "ai.claudecode.esgt2.vw.SnapshotIntegrationTest"
```

Expected: COMPILE ERROR (SnapshotService, VerificationSnapshotRepository 미존재)

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/ai/claudecode/esgt2/vw/SnapshotIntegrationTest.java
git commit -m "test: T-8-03~05 스냅샷 불변·격리·미승인 게이트 Red 테스트"
```

---

### Task 3: EsgErrorCode + JwtAuthentication 확장 (T-8-08 전제)

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java`
- Modify: `src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthentication.java`
- Modify: `src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/ai/claudecode/esgt2/shared/tenant/TenantContextInterceptor.java`

- [ ] **Step 1: EsgErrorCode에 신규 에러 코드 추가**

`src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java` 의 마지막 항목 다음에:

```java
// 기존 마지막 줄:
//    INTERNAL_ERROR
// 다음 줄 추가:
    REPORT_NOT_APPROVED,    // 미승인 보고서에 스냅샷 생성 시도
    SNAPSHOT_NOT_FOUND      // 스냅샷 미존재
```

결과:
```java
package ai.claudecode.esgt2.shared.exception;

public enum EsgErrorCode {
    RESOURCE_NOT_FOUND,
    ACCESS_DENIED,
    VALIDATION_FAILED,
    OPTIMISTIC_LOCK_CONFLICT,
    FORMULA_EVALUATION_FAILED,
    FORMULA_VALIDATION_FAILED,
    EMISSION_FACTOR_NOT_FOUND,
    INVALID_FILE_PATH,
    UNSUPPORTED_FILE_TYPE,
    REJECTION_REASON_REQUIRED,
    WEBHOOK_SIGNATURE_INVALID,
    CSV_PARSE_FAILED,
    INVITATION_NOT_FOUND,
    INVITATION_EXPIRED,
    INTERNAL_ERROR,
    REPORT_NOT_APPROVED,
    SNAPSHOT_NOT_FOUND
}
```

- [ ] **Step 2: JwtAuthentication에 snapshotId 필드 추가 (VERIFIER 전용)**

`src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthentication.java` 전체 교체:

```java
package ai.claudecode.esgt2.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tenantId;
    private final UUID entityId;    // SUPPLIER만 설정; 다른 역할은 null
    private final UUID snapshotId;  // VERIFIER만 설정; 다른 역할은 null

    public JwtAuthentication(UUID userId, UUID tenantId, List<String> roles) {
        this(userId, tenantId, null, null, roles);
    }

    public JwtAuthentication(UUID userId, UUID tenantId, UUID entityId, List<String> roles) {
        this(userId, tenantId, entityId, null, roles);
    }

    /** VERIFIER 역할 생성자 — snapshotId 포함 */
    public JwtAuthentication(UUID userId, UUID tenantId, UUID entityId,
                              UUID snapshotId, List<String> roles) {
        super(roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.userId = userId;
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.snapshotId = snapshotId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public UUID getPrincipal() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    /** SUPPLIER 역할 사용자의 스코프 법인 ID. 다른 역할은 null. */
    public UUID getEntityId() {
        return entityId;
    }

    /** VERIFIER 역할 사용자의 지정 스냅샷 ID. 다른 역할은 null. */
    public UUID getSnapshotId() {
        return snapshotId;
    }
}
```

- [ ] **Step 3: JwtAuthenticationFilter에 verifier_snapshot_id 파싱 추가**

`src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthenticationFilter.java` 전체 교체:

```java
package ai.claudecode.esgt2.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            var jwt = jwtTokenProvider.decode(token);
            List<String> roles = jwt.getClaimAsStringList("roles");
            UUID userId = UUID.fromString(jwt.getSubject());
            String tenantIdStr = jwt.getClaimAsString("tenantId");
            UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
            String entityIdStr = jwt.getClaimAsString("entityId");
            UUID entityId = entityIdStr != null ? UUID.fromString(entityIdStr) : null;
            // VERIFIER 전용: 지정 스냅샷 ID
            String snapshotIdStr = jwt.getClaimAsString("verifier_snapshot_id");
            UUID snapshotId = snapshotIdStr != null ? UUID.fromString(snapshotIdStr) : null;

            var authentication = new JwtAuthentication(userId, tenantId, entityId, snapshotId,
                roles == null ? List.of() : roles);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.warn("JWT filter: unexpected error processing token", e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: TenantContextInterceptor에 app.verifier_snapshot_id SET LOCAL 추가**

`src/main/java/ai/claudecode/esgt2/shared/tenant/TenantContextInterceptor.java` 전체 교체:

```java
package ai.claudecode.esgt2.shared.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * 요청마다 PostgreSQL 세션 변수를 설정해 RLS가 올바른 데이터만 반환하도록 한다.
 * SecurityFilter 이후, API 핸들러 이전에 실행된다.
 *
 * <p>설정되는 세션 변수:
 * <ul>
 *   <li>{@code app.current_tenant_id}: 모든 인증 요청에서 테넌트 격리 적용</li>
 *   <li>{@code app.verifier_snapshot_id}: VERIFIER 역할 요청에서 스냅샷 격리 적용</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantContextInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;

    private static final Pattern WEBHOOK_TENANT_PATTERN =
        Pattern.compile(".*/api/v1/intake/tenants/([^/]+)/webhook$");

    @Override
    public boolean preHandle(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler) {
        String tenantId = null;
        String snapshotId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwtAuth && jwtAuth.getTenantId() != null) {
            tenantId = jwtAuth.getTenantId().toString();
            // VERIFIER: 지정 스냅샷 ID도 세션에 설정
            if (jwtAuth.getSnapshotId() != null) {
                snapshotId = jwtAuth.getSnapshotId().toString();
            }
        } else {
            tenantId = extractWebhookTenantId(request.getRequestURI());
        }

        if (tenantId != null) {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.current_tenant_id', ?, true)",
                String.class, tenantId);
        }

        if (snapshotId != null) {
            jdbcTemplate.queryForObject(
                "SELECT set_config('app.verifier_snapshot_id', ?, true)",
                String.class, snapshotId);
        }

        return true;
    }

    private String extractWebhookTenantId(String requestUri) {
        var matcher = WEBHOOK_TENANT_PATTERN.matcher(requestUri);
        if (matcher.matches()) {
            try {
                java.util.UUID.fromString(matcher.group(1));
                return matcher.group(1);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
```

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java
git add src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthentication.java
git add src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthenticationFilter.java
git add src/main/java/ai/claudecode/esgt2/shared/tenant/TenantContextInterceptor.java
git commit -m "feat: VERIFIER JWT snapshotId claim + RLS verifier_snapshot_id 세션 설정"
```

---

### Task 4: vw 도메인 객체 (T-8-06)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/domain/VerificationSnapshot.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/domain/VerificationComment.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/domain/VerificationSignature.java`

- [ ] **Step 1: VerificationSnapshot 도메인 record 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/domain/VerificationSnapshot.java
package ai.claudecode.esgt2.vw.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 외부 검증 스냅샷 도메인 객체.
 * SHA-256 해시로 무결성을 보장하는 불변 record.
 */
public record VerificationSnapshot(
    UUID id,
    UUID tenantId,
    UUID reportId,
    String snapshotHash,     // SHA-256 hex (64자)
    String snapshotDataJson, // JSONB 원문
    Instant createdAt,
    Instant frozenAt
) {
    /**
     * 신규 스냅샷 생성 팩토리.
     *
     * @param tenantId       테넌트 ID
     * @param reportId       스냅샷 대상 보고서 ID
     * @param snapshotDataJson 보고서 내용 JSON 직렬화 결과
     */
    public static VerificationSnapshot create(UUID tenantId, UUID reportId,
                                               String snapshotDataJson) {
        String hash = sha256(snapshotDataJson);
        Instant now = Instant.now();
        return new VerificationSnapshot(
            UUID.randomUUID(), tenantId, reportId,
            hash, snapshotDataJson, now, now
        );
    }

    /**
     * SHA-256 해시 계산.
     *
     * @param content 해시 대상 문자열 (UTF-8)
     * @return 소문자 hex 문자열 (64자)
     */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JVM 표준 알고리즘 — 미지원 환경 없음
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR,
                "SHA-256 알고리즘 초기화 실패: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: VerificationComment 도메인 record 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/domain/VerificationComment.java
package ai.claudecode.esgt2.vw.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 검증 코멘트 도메인 객체.
 */
public record VerificationComment(
    UUID id,
    UUID snapshotId,
    UUID tenantId,
    UUID authorId,
    String body,
    Instant createdAt
) {
    public static VerificationComment create(UUID snapshotId, UUID tenantId,
                                              UUID authorId, String body) {
        if (body == null || body.isBlank()) {
            throw new ai.claudecode.esgt2.shared.exception.EsgException(
                ai.claudecode.esgt2.shared.exception.EsgErrorCode.VALIDATION_FAILED,
                "코멘트 내용은 필수입니다.");
        }
        return new VerificationComment(
            UUID.randomUUID(), snapshotId, tenantId, authorId, body, Instant.now()
        );
    }
}
```

- [ ] **Step 3: VerificationSignature 도메인 record 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/domain/VerificationSignature.java
package ai.claudecode.esgt2.vw.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 검증 완료 서명 도메인 객체.
 * 스냅샷당 1건만 허용 (verification_signatures.snapshot_id UNIQUE).
 */
public record VerificationSignature(
    UUID id,
    UUID snapshotId,
    UUID tenantId,
    UUID signedBy,
    Instant signedAt,
    String signNote
) {
    public static VerificationSignature create(UUID snapshotId, UUID tenantId,
                                                UUID signedBy, String signNote) {
        return new VerificationSignature(
            UUID.randomUUID(), snapshotId, tenantId, signedBy, Instant.now(), signNote
        );
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/domain/
git commit -m "feat: T-8-06 VerificationSnapshot·Comment·Signature 도메인 record (SHA-256)"
```

---

### Task 5: 인프라 계층 (JPA Entity + Repository)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSnapshotJpaEntity.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSnapshotRepository.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationCommentJpaEntity.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationCommentRepository.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSignatureJpaEntity.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSignatureRepository.java`

- [ ] **Step 1: VerificationSnapshotJpaEntity 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSnapshotJpaEntity.java
package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationSnapshot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSnapshotJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshotData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "frozen_at", nullable = false)
    private Instant frozenAt;

    public static VerificationSnapshotJpaEntity fromDomain(VerificationSnapshot domain) {
        var entity = new VerificationSnapshotJpaEntity();
        entity.id = domain.id();
        entity.tenantId = domain.tenantId();
        entity.reportId = domain.reportId();
        entity.snapshotHash = domain.snapshotHash();
        entity.snapshotData = domain.snapshotDataJson();
        entity.createdAt = domain.createdAt();
        entity.frozenAt = domain.frozenAt();
        return entity;
    }

    public VerificationSnapshot toDomain() {
        return new VerificationSnapshot(
            id, tenantId, reportId, snapshotHash, snapshotData, createdAt, frozenAt
        );
    }
}
```

- [ ] **Step 2: VerificationSnapshotRepository (append-only) 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSnapshotRepository.java
package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 스냅샷 저장소 — append-only.
 * VerificationSnapshot은 불변이므로 JpaRepository 대신 Repository 마커 인터페이스 상속.
 * delete* 메서드 컴파일 타임 미노출 (08-persistence.md).
 */
public interface VerificationSnapshotRepository
        extends Repository<VerificationSnapshotJpaEntity, UUID> {

    VerificationSnapshotJpaEntity save(VerificationSnapshotJpaEntity entity);

    Optional<VerificationSnapshotJpaEntity> findById(UUID id);

    Optional<VerificationSnapshotJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
```

- [ ] **Step 3: VerificationCommentJpaEntity 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationCommentJpaEntity.java
package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationComment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCommentJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static VerificationCommentJpaEntity fromDomain(VerificationComment domain) {
        var entity = new VerificationCommentJpaEntity();
        entity.id = domain.id();
        entity.snapshotId = domain.snapshotId();
        entity.tenantId = domain.tenantId();
        entity.authorId = domain.authorId();
        entity.body = domain.body();
        entity.createdAt = domain.createdAt();
        return entity;
    }

    public VerificationComment toDomain() {
        return new VerificationComment(id, snapshotId, tenantId, authorId, body, createdAt);
    }
}
```

- [ ] **Step 4: VerificationCommentRepository (append-only) 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationCommentRepository.java
package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

public interface VerificationCommentRepository
        extends Repository<VerificationCommentJpaEntity, UUID> {

    VerificationCommentJpaEntity save(VerificationCommentJpaEntity entity);

    List<VerificationCommentJpaEntity> findBySnapshotIdAndTenantIdOrderByCreatedAtAsc(
        UUID snapshotId, UUID tenantId);
}
```

- [ ] **Step 5: VerificationSignatureJpaEntity 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSignatureJpaEntity.java
package ai.claudecode.esgt2.vw.infra;

import ai.claudecode.esgt2.vw.domain.VerificationSignature;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_signatures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationSignatureJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, unique = true)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "signed_by", nullable = false)
    private UUID signedBy;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    @Column(name = "sign_note")
    private String signNote;

    public static VerificationSignatureJpaEntity fromDomain(VerificationSignature domain) {
        var entity = new VerificationSignatureJpaEntity();
        entity.id = domain.id();
        entity.snapshotId = domain.snapshotId();
        entity.tenantId = domain.tenantId();
        entity.signedBy = domain.signedBy();
        entity.signedAt = domain.signedAt();
        entity.signNote = domain.signNote();
        return entity;
    }

    public VerificationSignature toDomain() {
        return new VerificationSignature(id, snapshotId, tenantId, signedBy, signedAt, signNote);
    }
}
```

- [ ] **Step 6: VerificationSignatureRepository (append-only) 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/infra/VerificationSignatureRepository.java
package ai.claudecode.esgt2.vw.infra;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationSignatureRepository
        extends Repository<VerificationSignatureJpaEntity, UUID> {

    VerificationSignatureJpaEntity save(VerificationSignatureJpaEntity entity);

    Optional<VerificationSignatureJpaEntity> findBySnapshotIdAndTenantId(
        UUID snapshotId, UUID tenantId);

    boolean existsBySnapshotIdAndTenantId(UUID snapshotId, UUID tenantId);
}
```

- [ ] **Step 7: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/infra/
git commit -m "feat: vw 인프라 계층 — VerificationSnapshot·Comment·Signature JPA + append-only Repository"
```

---

### Task 6: SnapshotSecurityService (T-8-08)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/security/SnapshotSecurityService.java`

- [ ] **Step 1: SnapshotSecurityService 빈 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/security/SnapshotSecurityService.java
package ai.claudecode.esgt2.vw.security;

import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * VwController @PreAuthorize SpEL 보안 빈.
 *
 * <pre>
 * {@code @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")}
 * </pre>
 *
 * VERIFIER 역할이 자신에게 지정된 스냅샷 외의 스냅샷에 접근하려 하면 false 반환.
 */
@Component("snapshotSecurity")
public class SnapshotSecurityService {

    /**
     * 현재 인증 컨텍스트가 주어진 snapshotId에 접근 가능한지 검사.
     *
     * <ul>
     *   <li>VERIFIER: JWT claim {@code verifier_snapshot_id}와 요청 snapshotId가 일치해야 허용</li>
     *   <li>그 외 역할: true 반환 (역할 기반 체크는 hasRole()로 별도 처리)</li>
     * </ul>
     */
    public boolean canAccess(UUID snapshotId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwt) {
            // VERIFIER 역할인 경우: snapshotId가 JWT claim과 일치해야 허용
            boolean isVerifier = jwt.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VERIFIER".equals(a.getAuthority()));
            if (isVerifier) {
                return snapshotId != null && snapshotId.equals(jwt.getSnapshotId());
            }
        }
        return true;
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/security/SnapshotSecurityService.java
git commit -m "feat: T-8-08 SnapshotSecurityService — VERIFIER 스냅샷 접근 격리 SpEL 빈"
```

---

### Task 7: SnapshotService 인터페이스 + 응답 DTO (T-8-07)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/api/SnapshotService.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/api/SnapshotResponse.java`
- Create: `src/main/java/ai/claudecode/esgt2/vw/api/CommentResponse.java`
- Modify: `src/main/java/ai/claudecode/esgt2/vw/api/VwApi.java`

- [ ] **Step 1: SnapshotResponse DTO 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/api/SnapshotResponse.java
package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "검증 스냅샷 응답")
public record SnapshotResponse(
    @Schema(description = "스냅샷 ID") UUID id,
    @Schema(description = "테넌트 ID") UUID tenantId,
    @Schema(description = "보고서 ID") UUID reportId,
    @Schema(description = "SHA-256 해시 (64자)") String snapshotHash,
    @Schema(description = "생성 시각") Instant createdAt,
    @Schema(description = "서명 완료 여부") boolean signed
) {}
```

- [ ] **Step 2: CommentResponse DTO 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/api/CommentResponse.java
package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "검증 코멘트 응답")
public record CommentResponse(
    @Schema(description = "코멘트 ID") UUID id,
    @Schema(description = "스냅샷 ID") UUID snapshotId,
    @Schema(description = "작성자 ID") UUID authorId,
    @Schema(description = "내용") String body,
    @Schema(description = "작성 시각") Instant createdAt
) {}
```

- [ ] **Step 3: SnapshotService 인터페이스 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/api/SnapshotService.java
package ai.claudecode.esgt2.vw.api;

import java.util.List;
import java.util.UUID;

/**
 * 검증 워크스페이스 서비스 공개 인터페이스 (vw 모듈 api 패키지).
 * 다른 모듈에서 이 인터페이스만 통해 vw 기능에 접근.
 */
public interface SnapshotService {

    /** APPROVED 보고서에서 불변 스냅샷 생성 (T-8-07). 미승인 보고서 → EsgException(REPORT_NOT_APPROVED). */
    SnapshotResponse createSnapshot(UUID tenantId, UUID actorId, UUID reportId);

    /** 스냅샷 조회. 미존재 → EsgException(SNAPSHOT_NOT_FOUND). */
    SnapshotResponse getSnapshot(UUID tenantId, UUID snapshotId);

    /** 코멘트 작성 (T-8-09). @Auditable 적용. */
    CommentResponse addComment(UUID tenantId, UUID actorId, UUID snapshotId, String body);

    /** 코멘트 목록 조회 (생성 시각 오름차순). */
    List<CommentResponse> listComments(UUID tenantId, UUID snapshotId);

    /** 검증 완료 서명 (T-8-10). @Auditable 적용. */
    void signSnapshot(UUID tenantId, UUID actorId, UUID snapshotId, String note);

    /** 서명 여부 확인. */
    boolean isSigned(UUID tenantId, UUID snapshotId);
}
```

- [ ] **Step 4: VwApi.java 업데이트 (placeholder 제거)**

```java
// src/main/java/ai/claudecode/esgt2/vw/api/VwApi.java
package ai.claudecode.esgt2.vw.api;
// vw 모듈 공개 API — SnapshotService 참조
```

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/api/
git commit -m "feat: T-8-07 SnapshotService 인터페이스 + 응답 DTO"
```

---

### Task 8: DefaultSnapshotService 구현 (T-8-05, T-8-07, T-8-09, T-8-10)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/internal/DefaultSnapshotService.java`

- [ ] **Step 1: DefaultSnapshotService 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/internal/DefaultSnapshotService.java
package ai.claudecode.esgt2.vw.internal;

import ai.claudecode.esgt2.audit.api.Auditable;
import ai.claudecode.esgt2.rpt.api.ReportResponse;
import ai.claudecode.esgt2.rpt.api.ReportService;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.vw.api.CommentResponse;
import ai.claudecode.esgt2.vw.api.SnapshotResponse;
import ai.claudecode.esgt2.vw.api.SnapshotService;
import ai.claudecode.esgt2.vw.domain.VerificationComment;
import ai.claudecode.esgt2.vw.domain.VerificationSignature;
import ai.claudecode.esgt2.vw.domain.VerificationSnapshot;
import ai.claudecode.esgt2.vw.infra.VerificationCommentJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationCommentRepository;
import ai.claudecode.esgt2.vw.infra.VerificationSignatureJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationSignatureRepository;
import ai.claudecode.esgt2.vw.infra.VerificationSnapshotJpaEntity;
import ai.claudecode.esgt2.vw.infra.VerificationSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class DefaultSnapshotService implements SnapshotService {

    private final ReportService reportService;
    private final VerificationSnapshotRepository snapshotRepository;
    private final VerificationCommentRepository commentRepository;
    private final VerificationSignatureRepository signatureRepository;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────────
    // T-8-07: 스냅샷 생성 (APPROVED 보고서만)
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_CREATED")
    public SnapshotResponse createSnapshot(UUID tenantId, UUID actorId, UUID reportId) {
        // T-8-05: APPROVED 게이트
        if (!reportService.isApproved(tenantId, reportId)) {
            throw new EsgException(EsgErrorCode.REPORT_NOT_APPROVED,
                "APPROVED 상태의 보고서만 스냅샷을 생성할 수 있습니다.");
        }

        ReportResponse report = reportService.getReport(tenantId, reportId);
        String snapshotDataJson = buildSnapshotJson(report);

        VerificationSnapshot domain = VerificationSnapshot.create(tenantId, reportId, snapshotDataJson);
        var entity = VerificationSnapshotJpaEntity.fromDomain(domain);
        snapshotRepository.save(entity);

        log.info("스냅샷 생성 완료: snapshotId={}, reportId={}, hash={}",
            domain.id(), reportId, domain.snapshotHash());

        boolean signed = signatureRepository.existsBySnapshotIdAndTenantId(domain.id(), tenantId);
        return toResponse(domain, signed);
    }

    // ──────────────────────────────────────────────
    // T-8-07: 스냅샷 조회
    // ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public SnapshotResponse getSnapshot(UUID tenantId, UUID snapshotId) {
        var entity = snapshotRepository.findByIdAndTenantId(snapshotId, tenantId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId));

        boolean signed = signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId);
        return toResponse(entity.toDomain(), signed);
    }

    // ──────────────────────────────────────────────
    // T-8-09: 코멘트 작성
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_COMMENT_ADDED")
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public CommentResponse addComment(UUID tenantId, UUID actorId,
                                       UUID snapshotId, String body) {
        // 스냅샷 존재 확인
        if (!snapshotRepository.existsByIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId);
        }

        VerificationComment domain = VerificationComment.create(snapshotId, tenantId, actorId, body);
        var entity = VerificationCommentJpaEntity.fromDomain(domain);
        commentRepository.save(entity);

        return new CommentResponse(domain.id(), domain.snapshotId(), domain.authorId(),
            domain.body(), domain.createdAt());
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ESG_MANAGER') or @snapshotSecurity.canAccess(#snapshotId)")
    public List<CommentResponse> listComments(UUID tenantId, UUID snapshotId) {
        return commentRepository
            .findBySnapshotIdAndTenantIdOrderByCreatedAtAsc(snapshotId, tenantId)
            .stream()
            .map(e -> {
                var d = e.toDomain();
                return new CommentResponse(d.id(), d.snapshotId(), d.authorId(),
                    d.body(), d.createdAt());
            })
            .toList();
    }

    // ──────────────────────────────────────────────
    // T-8-10: 검증 완료 서명
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    @Auditable(action = "SNAPSHOT_SIGNED")
    @PreAuthorize("hasRole('VERIFIER') or hasRole('ESG_MANAGER')")
    public void signSnapshot(UUID tenantId, UUID actorId, UUID snapshotId, String note) {
        // 스냅샷 존재 확인
        if (!snapshotRepository.existsByIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.SNAPSHOT_NOT_FOUND,
                "스냅샷을 찾을 수 없습니다: " + snapshotId);
        }
        // 중복 서명 방지
        if (signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId)) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
                "이미 서명이 완료된 스냅샷입니다: " + snapshotId);
        }

        VerificationSignature domain =
            VerificationSignature.create(snapshotId, tenantId, actorId, note);
        signatureRepository.save(VerificationSignatureJpaEntity.fromDomain(domain));

        log.info("스냅샷 서명 완료: snapshotId={}, signedBy={}", snapshotId, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSigned(UUID tenantId, UUID snapshotId) {
        return signatureRepository.existsBySnapshotIdAndTenantId(snapshotId, tenantId);
    }

    // ──────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────

    /**
     * 보고서 데이터를 스냅샷 JSON으로 직렬화.
     * 필드 순서를 LinkedHashMap으로 고정하여 SHA-256 재현성 보장.
     */
    private String buildSnapshotJson(ReportResponse report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.id().toString());
        payload.put("entityId", report.entityId().toString());
        payload.put("reportingYear", report.reportingYear());
        payload.put("framework", report.framework());
        payload.put("status", report.status());
        payload.put("totalEmission", report.totalEmission() != null
            ? report.totalEmission().toPlainString() : null);
        payload.put("sections", report.sections().stream()
            .map(s -> Map.of(
                "itemCode", s.itemCode(),
                "value", s.value() != null ? s.value().toPlainString() : "0",
                "yoyDelta", s.yoyDelta() != null ? s.yoyDelta().toPlainString() : ""
            ))
            .toList());
        payload.put("approvedAt", report.approvedAt() != null
            ? report.approvedAt().toString() : null);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new EsgException(EsgErrorCode.INTERNAL_ERROR,
                "스냅샷 JSON 직렬화 실패: " + e.getMessage());
        }
    }

    private SnapshotResponse toResponse(VerificationSnapshot domain, boolean signed) {
        return new SnapshotResponse(
            domain.id(), domain.tenantId(), domain.reportId(),
            domain.snapshotHash(), domain.createdAt(), signed
        );
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS (Auditable import 경로 확인)

> **주의**: `@Auditable` import 경로는 `ai.claudecode.esgt2.audit.api.Auditable`. 빌드 오류 시 해당 경로 확인.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/internal/DefaultSnapshotService.java
git commit -m "feat: T-8-07~10 DefaultSnapshotService 구현"
```

---

### Task 9: VwController (T-8-07~10)

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/vw/api/VwController.java`

- [ ] **Step 1: VwController 작성**

```java
// src/main/java/ai/claudecode/esgt2/vw/api/VwController.java
package ai.claudecode.esgt2.vw.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Verification Workspace", description = "외부 검증 워크스페이스 API")
@RestController
@RequestMapping("/api/v1/vw/snapshots")
@RequiredArgsConstructor
public class VwController {

    private final SnapshotService snapshotService;

    @Operation(summary = "검증 스냅샷 생성",
        description = "APPROVED 보고서로부터 SHA-256 불변 스냅샷을 생성합니다.")
    @ApiResponse(responseCode = "201", description = "스냅샷 생성 성공")
    @ApiResponse(responseCode = "400", description = "미승인 보고서")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    @PostMapping
    public ResponseEntity<SnapshotResponse> createSnapshot(
            @RequestParam UUID tenantId,
            @RequestParam UUID reportId,
            @RequestAttribute(name = "actorId", required = false) UUID actorIdAttr,
            org.springframework.security.core.Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(201)
            .body(snapshotService.createSnapshot(tenantId, actorId, reportId));
    }

    @Operation(summary = "스냅샷 조회",
        description = "VERIFIER는 자신에게 지정된 스냅샷만 조회 가능합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "403", description = "접근 불가 (VERIFIER 격리)")
    @ApiResponse(responseCode = "404", description = "스냅샷 미존재")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @GetMapping("/{snapshotId}")
    public ResponseEntity<SnapshotResponse> getSnapshot(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId) {
        return ResponseEntity.ok(snapshotService.getSnapshot(tenantId, snapshotId));
    }

    @Operation(summary = "코멘트 작성",
        description = "검증 의견을 스냅샷에 기록합니다.")
    @ApiResponse(responseCode = "201", description = "코멘트 등록 성공")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @PostMapping("/{snapshotId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId,
            @RequestBody AddCommentRequest request,
            org.springframework.security.core.Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        return ResponseEntity.status(201)
            .body(snapshotService.addComment(tenantId, actorId, snapshotId, request.body()));
    }

    @Operation(summary = "코멘트 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @PreAuthorize("hasRole('ESG_MANAGER') or (hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId))")
    @GetMapping("/{snapshotId}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId) {
        return ResponseEntity.ok(snapshotService.listComments(tenantId, snapshotId));
    }

    @Operation(summary = "검증 완료 서명",
        description = "검증인이 스냅샷 검토를 완료하고 서명합니다. 1회만 허용.")
    @ApiResponse(responseCode = "204", description = "서명 완료")
    @ApiResponse(responseCode = "400", description = "이미 서명된 스냅샷")
    @PreAuthorize("hasRole('VERIFIER') or hasRole('ESG_MANAGER')")
    @PostMapping("/{snapshotId}/sign")
    public ResponseEntity<Void> signSnapshot(
            @RequestParam UUID tenantId,
            @PathVariable UUID snapshotId,
            @RequestBody(required = false) SignRequest request,
            org.springframework.security.core.Authentication auth) {
        UUID actorId = (UUID) auth.getPrincipal();
        String note = request != null ? request.note() : null;
        snapshotService.signSnapshot(tenantId, actorId, snapshotId, note);
        return ResponseEntity.noContent().build();
    }

    /** 코멘트 요청 DTO */
    public record AddCommentRequest(String body) {}

    /** 서명 요청 DTO (note 선택) */
    public record SignRequest(String note) {}
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/ai/claudecode/esgt2/vw/api/VwController.java
git commit -m "feat: VwController — 스냅샷 생성·조회·코멘트·서명 REST API"
```

---

### Task 10: 테스트 통과 확인 (T-8-03, T-8-04, T-8-05, T-8-09, T-8-10)

- [ ] **Step 1: vw 통합 테스트 실행**

```bash
./gradlew test --tests "ai.claudecode.esgt2.vw.SnapshotIntegrationTest"
```

Expected: 7건 모두 PASS

실패 시 진단 포인트:
- `REPORT_NOT_APPROVED` 오류 → EsgException import 확인
- `AccessDeniedException` 아닌 다른 예외 → SnapshotSecurityService bean 등록 확인 (`@Component("snapshotSecurity")`)
- T-8-03 append-only 테스트 → `VerificationSnapshotRepository extends Repository<>` 확인
- `JwtAuthentication 4-arg constructor` → Task 3에서 수정한 생성자 확인
- `snapshotService` null → `DefaultSnapshotService` package-private class가 Spring bean으로 등록되는지 확인 (`@Service` 있어야 함)

- [ ] **Step 2: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: 기존 테스트 포함 모두 통과 (ActuatorEndpointTest 1건 기존 실패는 Phase 8 이전부터 존재하므로 무시)

- [ ] **Step 3: task.md Phase 8 완료 체크**

`docs/task.md`의 Phase 8 섹션을 모두 DONE으로 업데이트:

```markdown
| T-8-01 | V23__verification_tables.sql | DONE | verification_snapshots, verification_comments, verification_signatures |
| T-8-02 | PostgreSQL 트리거: 스냅샷 UPDATE/DELETE 차단 | DONE | migration-pg/V23 |
| T-8-03 | `test:` 스냅샷 불변성 확인 (append-only Repository + hash) | DONE | |
| T-8-04 | `test:` VERIFIER → 지정 스냅샷 외 접근 → 403 | DONE | |
| T-8-05 | `test:` 미승인 보고서 → 스냅샷 생성 시도 → 예외 | DONE | |
| T-8-06 | `feat:` VerificationSnapshot 도메인 (SHA-256 해시) | DONE | |
| T-8-07 | `feat:` 스냅샷 생성 API (POST /api/v1/vw/snapshots) | DONE | APPROVED 보고서만 |
| T-8-08 | `feat:` VERIFIER RLS 정책 (지정 snapshot_id만) | DONE | |
| T-8-09 | `feat:` 코멘트 CRUD (POST /api/v1/vw/snapshots/{id}/comments) | DONE | @Auditable |
| T-8-10 | `feat:` 검증 완료 서명 (POST /api/v1/vw/snapshots/{id}/sign) | DONE | @Auditable |
```

- [ ] **Step 4: 커밋**

```bash
git add docs/task.md
git commit -m "docs: Phase 8 T-8-01~10 완료 체크"
```

---

### Task 11: ModularityTest 통과 확인

- [ ] **Step 1: ModularityTest 실행**

```bash
./gradlew test --tests "*ModularityTest"
```

Expected: PASS

실패 시 진단:
- vw 모듈이 rpt.internal 패키지를 참조 → `rpt.api.ReportService` 사용으로 교체
- audit 모듈 참조 → `@Auditable` import가 `audit.api.Auditable` 경로인지 확인
- security 패키지가 모듈 경계 위반 → `vw.security` 하위 패키지는 vw 내부이므로 문제없음

- [ ] **Step 2: 전체 빌드 최종 확인**

```bash
./gradlew build
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 최종 커밋**

```bash
git add .
git commit -m "test: Phase 8 ModularityTest + 전체 테스트 통과 확인"
```

---

## 자가 검토 (Spec Coverage)

| 요구사항 | Task |
|---|---|
| T-8-01: verification_snapshots, verification_comments DDL | Task 1 |
| T-8-02: PostgreSQL 트리거 UPDATE/DELETE 차단 | Task 1 (migration-pg) |
| T-8-03: 스냅샷 불변성 확인 | Task 2, Task 10 |
| T-8-04: VERIFIER 스냅샷 격리 403 | Task 2, Task 6, Task 10 |
| T-8-05: 미승인 보고서 게이트 | Task 2, Task 8 |
| T-8-06: VerificationSnapshot 도메인 (SHA-256) | Task 4 |
| T-8-07: 스냅샷 생성 API | Task 7, Task 8, Task 9 |
| T-8-08: VERIFIER RLS + SnapshotSecurityService | Task 1(migration-pg), Task 3, Task 6 |
| T-8-09: 코멘트 CRUD @Auditable | Task 5, Task 8, Task 9 |
| T-8-10: 검증 완료 서명 @Auditable | Task 5, Task 8, Task 9 |
| spec.md VERIFIER 격리 JWT → RLS | Task 3 (JwtAuthentication + Filter + Interceptor) |
| append-only Repository | Task 5 (extends Repository) |
| 08-persistence.md REVOKE UPDATE | Task 1 (migration-pg) |

✅ 모든 요구사항이 Task에 대응됨.
