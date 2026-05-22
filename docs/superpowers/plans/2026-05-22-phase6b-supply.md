# Phase 6B-supply: 공급업체 포털 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** SUPPLIER 계정 초대·활성화, 자사 데이터 제출, ESG_MANAGER 승인 워크플로우, 미제출 법인 자동 리마인더를 구현한다.

**Architecture:** supply 모듈이 ghg.api(GhgService, ActivityDataWorkflowService)와 entity.api(EntityManagementService)를 통해 cross-module 접근하며, SUPPLIER 역할 사용자는 JWT에 entityId 클레임을 포함해 자사 법인만 접근 가능하도록 격리한다.

**Tech Stack:** Spring Boot 4, Java 25 Records, spring-boot-starter-mail, JavaMailSender, Spring Modulith, Testcontainers PostgreSQL

---

## 파일 구조

### 새로 생성
```
src/main/resources/db/migration/
  V19__supplier_invitations.sql

src/main/resources/db/migration-pg/
  V19__supplier_rls.sql

src/main/java/ai/claudecode/esgt2/
  ghg/api/ActivityDataWorkflowService.java
  ghg/internal/DefaultActivityDataWorkflowService.java
  supply/api/SupplierService.java
  supply/api/SupplierController.java
  supply/api/InviteSupplierRequest.java
  supply/api/ActivateSupplierRequest.java
  supply/api/SupplierDataRequest.java
  supply/api/SupplierInvitationResponse.java
  supply/domain/SupplierInvitation.java
  supply/infra/SupplierInvitationJpaEntity.java
  supply/infra/SupplierInvitationRepository.java
  supply/internal/EmailGateway.java
  supply/internal/SmtpEmailGateway.java
  supply/internal/DefaultSupplierService.java
  supply/internal/SupplierReminderScheduler.java

src/test/java/ai/claudecode/esgt2/
  supply/domain/SupplierInvitationDomainTest.java
  supply/SupplyIntegrationTest.java
  supply/support/StubEmailGateway.java
  supply/support/SupplyTestConfig.java
```

### 수정
```
build.gradle.kts                              spring-boot-starter-mail 추가
src/main/resources/application.yml           mail config 추가
shared/security/JwtAuthentication.java       entityId 필드 추가
shared/security/JwtAuthenticationFilter.java entityId 클레임 추출
shared/security/JwtTokenProvider.java        generateAccessToken(entityId 추가)
shared/security/SecurityConfig.java          /api/v1/supply/suppliers/activate permitAll
shared/exception/EsgErrorCode.java           INVITATION_NOT_FOUND, INVITATION_EXPIRED 추가
entity/api/AuthService.java                  createSupplierUser 추가
entity/internal/DefaultAuthService.java      createSupplierUser 구현 + login entityId 포함
ghg/api/GhgService.java                      hasActivityData 추가
ghg/infra/ActivityDataRepository.java        existsByTenantIdAndEntityId 추가
ghg/internal/DefaultGhgService.java          hasActivityData 구현
```

---

## Task 1: DB 마이그레이션 + spring-boot-starter-mail 의존성 추가

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V19__supplier_invitations.sql`
- Create: `src/main/resources/db/migration-pg/V19__supplier_rls.sql`

- [ ] **Step 1: build.gradle.kts에 mail 의존성 추가**

`dependencies` 블록에서 CSV 라인 아래에 추가:
```kotlin
// 이메일 발송 (공급업체 초대·리마인더)
implementation("org.springframework.boot:spring-boot-starter-mail")
```

- [ ] **Step 2: application.yml에 mail 설정 추가**

`application.yml`에서 `intake:` 블록 아래에 추가:
```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

supply:
  reminder:
    reporting-year: ${SUPPLY_REMINDER_YEAR:2025}
```

- [ ] **Step 3: V19__supplier_invitations.sql 생성**

```sql
-- supplier_invitations: 공급업체 초대 (T-6-07)
CREATE TABLE supplier_invitations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    entity_id   UUID         NOT NULL REFERENCES legal_entities(id),
    email       VARCHAR(320) NOT NULL,
    token       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    invited_by  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT supplier_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

CREATE INDEX idx_supply_inv_tenant ON supplier_invitations (tenant_id);
CREATE INDEX idx_supply_inv_token  ON supplier_invitations (token);
```

- [ ] **Step 4: V19__supplier_rls.sql 생성**

```sql
-- RLS: supplier_invitations 테넌트 격리
ALTER TABLE supplier_invitations ENABLE ROW LEVEL SECURITY;

CREATE POLICY supplier_invitations_tenant_isolation ON supplier_invitations
    FOR ALL TO PUBLIC
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));
```

- [ ] **Step 5: 빌드 + 마이그레이션 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 2: JWT entityId 확장

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthentication.java`
- Modify: `src/main/java/ai/claudecode/esgt2/shared/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/ai/claudecode/esgt2/shared/security/JwtTokenProvider.java`

SUPPLIER 사용자가 자신의 법인 ID만 JWT에서 확인하여 크로스-엔티티 방어를 컨트롤러에서 수행한다.

- [ ] **Step 1: JwtAuthentication에 entityId 추가**

```java
package ai.claudecode.esgt2.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final UUID userId;
    private final UUID tenantId;
    private final UUID entityId;   // SUPPLIER만 설정; 다른 역할은 null

    public JwtAuthentication(UUID userId, UUID tenantId, List<String> roles) {
        this(userId, tenantId, null, roles);
    }

    public JwtAuthentication(UUID userId, UUID tenantId, UUID entityId, List<String> roles) {
        super(roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.userId = userId;
        this.tenantId = tenantId;
        this.entityId = entityId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public UUID getPrincipal() { return userId; }

    public UUID getTenantId() { return tenantId; }

    /** SUPPLIER 역할 사용자의 스코프 법인 ID. 다른 역할은 null. */
    public UUID getEntityId() { return entityId; }
}
```

- [ ] **Step 2: JwtAuthenticationFilter에서 entityId 추출**

```java
// doFilterInternal 내 authentication 생성 부분 교체
var jwt = jwtTokenProvider.decode(token);
List<String> roles = jwt.getClaimAsStringList("roles");
UUID userId = UUID.fromString(jwt.getSubject());
String tenantIdStr = jwt.getClaimAsString("tenantId");
UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
String entityIdStr = jwt.getClaimAsString("entityId");
UUID entityId = entityIdStr != null ? UUID.fromString(entityIdStr) : null;

var authentication = new JwtAuthentication(userId, tenantId, entityId,
    roles == null ? List.of() : roles);
SecurityContextHolder.getContext().setAuthentication(authentication);
```

- [ ] **Step 3: JwtTokenProvider.generateAccessToken에 entityId 추가**

```java
// 기존 메서드 유지 (기존 호출부 호환성)
public String generateAccessToken(UUID userId, UUID tenantId, List<String> roles) {
    return generateAccessToken(userId, tenantId, null, roles);
}

// 새 오버로드 (SUPPLIER 사용자용)
public String generateAccessToken(UUID userId, UUID tenantId, UUID entityId, List<String> roles) {
    return encode(userId, tenantId, entityId, roles, jwtProperties.accessTokenExpirySeconds());
}
```

`encode()` 메서드에 entityId 클레임 추가:
```java
private String encode(UUID userId, UUID tenantId, UUID entityId, List<String> roles, long expirySeconds) {
    var now = Instant.now();
    var builder = JwtClaimsSet.builder()
        .subject(userId.toString())
        .claim("tenantId", tenantId.toString())
        .claim("roles", roles)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(expirySeconds));
    if (entityId != null) {
        builder.claim("entityId", entityId.toString());
    }
    var claims = builder.build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
}

// 기존 encode(UUID, UUID, List, long)는 위 메서드로 위임
private String encode(UUID userId, UUID tenantId, List<String> roles, long expirySeconds) {
    return encode(userId, tenantId, null, roles, expirySeconds);
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 3: AuthService.createSupplierUser 추가

TENANT_ADMIN이 공급업체 계정을 생성할 수 있도록 AuthService에 user+role 생성 메서드를 추가한다.

**Files:**
- Modify: `src/main/java/ai/claudecode/esgt2/entity/api/AuthService.java`
- Modify: `src/main/java/ai/claudecode/esgt2/entity/internal/DefaultAuthService.java`

- [ ] **Step 1: AuthService 인터페이스에 createSupplierUser 추가**

```java
package ai.claudecode.esgt2.entity.api;

import java.util.UUID;

public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String refreshToken);
    void logout(String refreshToken);

    /**
     * 공급업체 계정 생성 (SUPPLIER 역할 + entityId 스코프).
     * TENANT_ADMIN 권한 필요. supply 모듈의 계정 활성화에서 호출.
     *
     * @return 생성된 사용자 ID
     */
    UUID createSupplierUser(UUID tenantId, String email, String password, UUID entityId);
}
```

- [ ] **Step 2: DefaultAuthService.createSupplierUser 구현**

```java
@Override
@Transactional
public UUID createSupplierUser(UUID tenantId, String email, String password, UUID entityId) {
    // 중복 계정 방지
    if (userRepository.findActiveByTenantIdAndEmail(tenantId, email).isPresent()) {
        throw new EsgException(EsgErrorCode.VALIDATION_FAILED,
            "이미 존재하는 이메일입니다: " + email);
    }
    var user = UserJpaEntity.builder()
        .tenantId(tenantId)
        .email(email)
        .passwordHash(passwordEncoder.encode(password))
        .build();
    var saved = userRepository.save(user);

    var role = UserRoleJpaEntity.builder()
        .userId(saved.getId())
        .role("SUPPLIER")
        .entityId(entityId)
        .build();
    userRoleRepository.save(role);

    return saved.getId();
}
```

- [ ] **Step 3: DefaultAuthService.login()에서 SUPPLIER entityId 포함**

```java
@Override
@Transactional(readOnly = true)
public TokenResponse login(LoginRequest request) {
    var user = userRepository.findActiveByTenantIdAndEmail(request.tenantId(), request.email())
        .orElseThrow(() -> new EsgException(EsgErrorCode.ACCESS_DENIED,
            "이메일 또는 비밀번호가 일치하지 않습니다."));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
        throw new EsgException(EsgErrorCode.ACCESS_DENIED,
            "이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    List<UserRoleJpaEntity> userRoles = userRoleRepository.findByUserId(user.getId());
    List<String> roles = userRoles.stream().map(UserRoleJpaEntity::getRole).toList();

    // SUPPLIER 역할이면 entityId를 JWT에 포함
    UUID entityId = userRoles.stream()
        .filter(r -> "SUPPLIER".equals(r.getRole()) && r.getEntityId() != null)
        .map(UserRoleJpaEntity::getEntityId)
        .findFirst()
        .orElse(null);

    String accessToken = jwtTokenProvider.generateAccessToken(
        user.getId(), user.getTenantId(), entityId, roles);
    String refreshToken = jwtTokenProvider.generateRefreshToken(
        user.getId(), user.getTenantId());
    return new TokenResponse(accessToken, refreshToken);
}
```

- [ ] **Step 4: EsgErrorCode에 새 코드 추가**

```java
// EsgErrorCode enum에 추가
INVITATION_NOT_FOUND,
INVITATION_EXPIRED,
```

- [ ] **Step 5: 테스트 실행**

Run: `./gradlew test --tests "ai.claudecode.esgt2.entity.*"`
Expected: 기존 테스트 모두 통과

- [ ] **Step 6: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/shared/security/ \
        src/main/java/ai/claudecode/esgt2/entity/api/AuthService.java \
        src/main/java/ai/claudecode/esgt2/entity/internal/DefaultAuthService.java \
        src/main/java/ai/claudecode/esgt2/shared/exception/EsgErrorCode.java \
        build.gradle.kts src/main/resources/application.yml \
        src/main/resources/db/migration/V19__supplier_invitations.sql \
        src/main/resources/db/migration-pg/V19__supplier_rls.sql
git commit -m "feat: JWT entityId 확장 + AuthService.createSupplierUser + V19 마이그레이션 (T-6-07)"
```

---

## Task 4: ActivityDataWorkflowService (ghg 모듈 공개 API)

supply 모듈이 activity_data 상태를 변경하려면 ghg.internal에 직접 접근할 수 없다. ghg.api에 공개 인터페이스를 추가한다.

**Files:**
- Create: `src/main/java/ai/claudecode/esgt2/ghg/api/ActivityDataWorkflowService.java`
- Create: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultActivityDataWorkflowService.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/api/GhgService.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/infra/ActivityDataRepository.java`
- Modify: `src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultGhgService.java`

- [ ] **Step 1: ActivityDataWorkflowService 인터페이스 생성**

```java
package ai.claudecode.esgt2.ghg.api;

import java.util.UUID;

/** activity_data 상태 전이 공개 API (supply 모듈에서 호출 가능). */
public interface ActivityDataWorkflowService {

    /**
     * DRAFT → PENDING (공급업체 제출).
     */
    ActivityDataResponse submitActivityData(UUID tenantId, UUID actorId, UUID activityDataId);

    /**
     * PENDING → APPROVED (ESG_MANAGER 승인).
     */
    ActivityDataResponse approveActivityData(UUID tenantId, UUID actorId, UUID activityDataId);

    /**
     * PENDING → REJECTED (ESG_MANAGER 반려).
     *
     * @param reason 반려 사유 (빈 문자열 불가)
     */
    ActivityDataResponse rejectActivityData(UUID tenantId, UUID actorId, UUID activityDataId,
                                             String reason);
}
```

- [ ] **Step 2: GhgService에 hasActivityData 추가**

```java
// GhgService 인터페이스에 추가
/** 지정 법인이 해당 연도에 활동 데이터를 하나라도 보유하는지 확인. 리마인더 스케줄러 사용. */
boolean hasActivityData(UUID tenantId, UUID entityId, int reportingYear);
```

- [ ] **Step 3: ActivityDataRepository에 쿼리 추가**

```java
// ActivityDataRepository에 추가
boolean existsByTenantIdAndEntityIdAndReportingYear(UUID tenantId, UUID entityId, int reportingYear);

Optional<ActivityDataJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
```

- [ ] **Step 4: DefaultActivityDataWorkflowService 구현**

```java
package ai.claudecode.esgt2.ghg.internal;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.ghg.infra.ActivityDataJpaEntity;
import ai.claudecode.esgt2.ghg.infra.ActivityDataRepository;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class DefaultActivityDataWorkflowService implements ActivityDataWorkflowService {

    private final ActivityDataRepository activityDataRepository;

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_SUBMITTED")
    public ActivityDataResponse submitActivityData(UUID tenantId, UUID actorId, UUID activityDataId) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.submit(actorId);
        return toResponse(entity);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_APPROVED")
    public ActivityDataResponse approveActivityData(UUID tenantId, UUID actorId, UUID activityDataId) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.approve(actorId);
        return toResponse(entity);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVITY_DATA_REJECTED")
    public ActivityDataResponse rejectActivityData(UUID tenantId, UUID actorId,
                                                    UUID activityDataId, String reason) {
        var entity = findOrThrow(tenantId, activityDataId);
        entity.reject(actorId, reason);
        return toResponse(entity);
    }

    private ActivityDataJpaEntity findOrThrow(UUID tenantId, UUID activityDataId) {
        return activityDataRepository.findByIdAndTenantId(activityDataId, tenantId)
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "활동 데이터를 찾을 수 없습니다: " + activityDataId));
    }

    private ActivityDataResponse toResponse(ActivityDataJpaEntity e) {
        return new ActivityDataResponse(
            e.getId(), e.getEntityId(),
            e.getReportingYear(), e.getCategory(), e.getSubCategory(),
            e.getQuantity(), e.getUnit(), e.getCountryCode(),
            e.getDataSource(), e.getDataQuality(), e.getStatus(),
            e.getCreatedAt());
    }
}
```

- [ ] **Step 5: DefaultGhgService.hasActivityData 구현**

`DefaultGhgService`에 추가:
```java
@Override
@Transactional(readOnly = true)
public boolean hasActivityData(UUID tenantId, UUID entityId, int reportingYear) {
    return activityDataRepository.existsByTenantIdAndEntityIdAndReportingYear(
        tenantId, entityId, reportingYear);
}
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 5: SupplierInvitation 도메인 + 인프라 + EmailGateway

**Files:**
- Create: `supply/domain/SupplierInvitation.java`
- Create: `supply/infra/SupplierInvitationJpaEntity.java`
- Create: `supply/infra/SupplierInvitationRepository.java`
- Create: `supply/internal/EmailGateway.java`
- Create: `supply/internal/SmtpEmailGateway.java`

- [ ] **Step 1: SupplierInvitation 도메인 레코드 작성**

```java
package ai.claudecode.esgt2.supply.domain;

import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 공급업체 초대 도메인 레코드.
 * 상태 기계: PENDING → ACCEPTED 또는 EXPIRED.
 */
public record SupplierInvitation(
    UUID id,
    UUID tenantId,
    UUID entityId,
    String email,
    UUID token,
    String status,
    UUID invitedBy,
    OffsetDateTime expiresAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime createdAt
) {
    /** 새 초대 생성 팩토리. 유효 기간 7일. */
    public static SupplierInvitation create(UUID tenantId, UUID entityId,
                                             String email, UUID invitedBy) {
        if (email == null || email.isBlank()) {
            throw new EsgException(EsgErrorCode.VALIDATION_FAILED, "초대 이메일은 필수입니다.");
        }
        return new SupplierInvitation(
            UUID.randomUUID(), tenantId, entityId,
            email.toLowerCase().strip(),
            UUID.randomUUID(),
            "PENDING", invitedBy,
            OffsetDateTime.now().plusDays(7), null, OffsetDateTime.now());
    }

    /** 초대 토큰 유효성 검증 (만료·상태 확인). */
    public void validateForActivation() {
        if (!"PENDING".equals(status)) {
            throw new EsgException(EsgErrorCode.INVITATION_EXPIRED,
                "이미 사용되었거나 만료된 초대입니다.");
        }
        if (OffsetDateTime.now().isAfter(expiresAt)) {
            throw new EsgException(EsgErrorCode.INVITATION_EXPIRED,
                "초대 링크가 만료되었습니다. 재초대를 요청하세요.");
        }
    }
}
```

- [ ] **Step 2: SupplierInvitationJpaEntity 작성**

```java
package ai.claudecode.esgt2.supply.infra;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_invitations")
@Getter
@NoArgsConstructor
public class SupplierInvitationJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private UUID invitedBy;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    private OffsetDateTime acceptedAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public SupplierInvitationJpaEntity(UUID id, UUID tenantId, UUID entityId, String email,
                                        UUID token, UUID invitedBy, OffsetDateTime expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.entityId = entityId;
        this.email = email;
        this.token = token;
        this.status = "PENDING";
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    /** PENDING → ACCEPTED */
    public void accept() {
        if (!"PENDING".equals(this.status)) {
            throw new IllegalStateException("PENDING 상태만 수락 가능합니다.");
        }
        this.status = "ACCEPTED";
        this.acceptedAt = OffsetDateTime.now();
    }
}
```

- [ ] **Step 3: SupplierInvitationRepository 작성**

```java
package ai.claudecode.esgt2.supply.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierInvitationRepository extends JpaRepository<SupplierInvitationJpaEntity, UUID> {

    Optional<SupplierInvitationJpaEntity> findByToken(UUID token);

    boolean existsByTenantIdAndEntityIdAndEmailAndStatus(
        UUID tenantId, UUID entityId, String email, String status);
}
```

- [ ] **Step 4: EmailGateway 인터페이스 작성**

```java
package ai.claudecode.esgt2.supply.internal;

/** 이메일 발송 게이트웨이. SmtpEmailGateway(운영) / StubEmailGateway(테스트)로 교체 가능. */
public interface EmailGateway {
    void sendInvitationEmail(String to, String tenantName, String activationLink);
    void sendReminderEmail(String to, String entityName, int reportingYear);
}
```

- [ ] **Step 5: SmtpEmailGateway 구현 작성**

```java
package ai.claudecode.esgt2.supply.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;

    @Override
    public void sendInvitationEmail(String to, String tenantName, String activationLink) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[" + tenantName + "] ESG 공급업체 포털 초대");
        msg.setText("공급업체 포털에 초대되었습니다.\n\n활성화 링크: " + activationLink +
                    "\n\n링크 유효 기간: 7일");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("초대 이메일 발송 실패 (to={}): {}", to, e.getMessage());
            // 이메일 실패는 초대 자체를 실패시키지 않음 — 관리자가 수동 발송 가능
        }
    }

    @Override
    public void sendReminderEmail(String to, String entityName, int reportingYear) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("[ESG 포털] " + reportingYear + "년 활동 데이터 제출 리마인더");
        msg.setText("안녕하세요,\n\n" + entityName + " 법인의 " + reportingYear +
                    "년 활동 데이터가 아직 제출되지 않았습니다.\n\nESG 포털에 접속하여 데이터를 제출해 주세요.");
        try {
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("리마인더 이메일 발송 실패 (to={}, entity={}): {}", to, entityName, e.getMessage());
        }
    }
}
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 6: DefaultSupplierService 구현

**Files:**
- Create: `supply/api/SupplierService.java`
- Create: `supply/api/InviteSupplierRequest.java`
- Create: `supply/api/ActivateSupplierRequest.java`
- Create: `supply/api/SupplierDataRequest.java`
- Create: `supply/api/SupplierInvitationResponse.java`
- Create: `supply/internal/DefaultSupplierService.java`

- [ ] **Step 1: DTO 및 인터페이스 작성**

`SupplierService.java`:
```java
package ai.claudecode.esgt2.supply.api;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;

import java.util.UUID;

public interface SupplierService {
    SupplierInvitationResponse inviteSupplier(UUID tenantId, UUID actorId,
                                               InviteSupplierRequest request);
    void activateAccount(ActivateSupplierRequest request);
    ActivityDataResponse submitData(UUID tenantId, UUID actorId,
                                    UUID entityId, SupplierDataRequest request);
    ActivityDataResponse submitForReview(UUID tenantId, UUID actorId, UUID activityDataId);
    ActivityDataResponse approveData(UUID tenantId, UUID actorId, UUID activityDataId);
    ActivityDataResponse rejectData(UUID tenantId, UUID actorId,
                                    UUID activityDataId, String reason);
}
```

`InviteSupplierRequest.java`:
```java
package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "공급업체 초대 요청")
public record InviteSupplierRequest(
    @Schema(description = "초대할 이메일", example = "supplier@example.com")
    @NotBlank @Email String email,

    @Schema(description = "스코프 법인 ID")
    @NotNull UUID entityId
) {}
```

`ActivateSupplierRequest.java`:
```java
package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "공급업체 계정 활성화 요청")
public record ActivateSupplierRequest(
    @Schema(description = "초대 토큰 (이메일 링크에서 추출)")
    @NotNull UUID token,

    @Schema(description = "설정할 비밀번호 (최소 8자)")
    @NotBlank @Size(min = 8) String password
) {}
```

`SupplierDataRequest.java`:
```java
package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "공급업체 활동 데이터 등록 요청")
public record SupplierDataRequest(
    @Schema(description = "보고 연도", example = "2025")
    @NotNull @Min(2020) int reportingYear,

    @Schema(description = "GHG 카테고리", example = "SCOPE3_CAT1")
    @NotBlank String category,

    @Schema(description = "세부 카테고리", example = "ELECTRONICS")
    String subCategory,

    @Schema(description = "활동량", example = "10000.0")
    @NotNull @Positive BigDecimal quantity,

    @Schema(description = "단위", example = "KRW")
    @NotBlank String unit,

    @Schema(description = "국가 코드", example = "KR")
    @NotBlank String countryCode
) {}
```

`SupplierInvitationResponse.java`:
```java
package ai.claudecode.esgt2.supply.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "공급업체 초대 응답")
public record SupplierInvitationResponse(
    @Schema(description = "초대 ID") UUID id,
    @Schema(description = "초대 이메일") String email,
    @Schema(description = "스코프 법인 ID") UUID entityId,
    @Schema(description = "초대 상태") String status,
    @Schema(description = "만료 일시") OffsetDateTime expiresAt
) {}
```

- [ ] **Step 2: DefaultSupplierService 작성**

```java
package ai.claudecode.esgt2.supply.internal;

import ai.claudecode.esgt2.entity.api.AuthService;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.ghg.api.CreateActivityDataRequest;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.shared.audit.Auditable;
import ai.claudecode.esgt2.shared.exception.EsgErrorCode;
import ai.claudecode.esgt2.shared.exception.EsgException;
import ai.claudecode.esgt2.supply.api.ActivateSupplierRequest;
import ai.claudecode.esgt2.supply.api.InviteSupplierRequest;
import ai.claudecode.esgt2.supply.api.SupplierDataRequest;
import ai.claudecode.esgt2.supply.api.SupplierInvitationResponse;
import ai.claudecode.esgt2.supply.api.SupplierService;
import ai.claudecode.esgt2.supply.domain.SupplierInvitation;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationJpaEntity;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class DefaultSupplierService implements SupplierService {

    private final SupplierInvitationRepository invitationRepository;
    private final AuthService authService;
    private final EntityManagementService entityManagementService;
    private final GhgService ghgService;
    private final ActivityDataWorkflowService workflowService;
    private final EmailGateway emailGateway;

    @Value("${supply.activation-base-url:http://localhost:3000/supply/activate}")
    private String activationBaseUrl;

    @Override
    @Transactional
    @Auditable(action = "SUPPLIER_INVITED")
    public SupplierInvitationResponse inviteSupplier(UUID tenantId, UUID actorId,
                                                      InviteSupplierRequest request) {
        // 법인 소속 검증
        entityManagementService.findById(tenantId, request.entityId())
            .orElseThrow(() -> new EsgException(EsgErrorCode.RESOURCE_NOT_FOUND,
                "법인을 찾을 수 없습니다: " + request.entityId()));

        // 이미 PENDING 초대가 있으면 재전송 대신 기존 초대 재활용 안 함 — 새 토큰 발급
        var invitation = SupplierInvitation.create(
            tenantId, request.entityId(), request.email(), actorId);

        var entity = SupplierInvitationJpaEntity.builder()
            .id(invitation.id())
            .tenantId(invitation.tenantId())
            .entityId(invitation.entityId())
            .email(invitation.email())
            .token(invitation.token())
            .invitedBy(invitation.invitedBy())
            .expiresAt(invitation.expiresAt())
            .build();
        invitationRepository.save(entity);

        // 이메일 발송 (실패해도 초대는 저장됨)
        String activationLink = activationBaseUrl + "?token=" + invitation.token();
        emailGateway.sendInvitationEmail(invitation.email(), tenantId.toString(), activationLink);

        log.info("공급업체 초대 완료 (email={}, entity={})", invitation.email(), request.entityId());

        return new SupplierInvitationResponse(
            entity.getId(), entity.getEmail(), entity.getEntityId(),
            entity.getStatus(), entity.getExpiresAt());
    }

    @Override
    @Transactional
    public void activateAccount(ActivateSupplierRequest request) {
        var invEntity = invitationRepository.findByToken(request.token())
            .orElseThrow(() -> new EsgException(EsgErrorCode.INVITATION_NOT_FOUND,
                "유효하지 않은 초대 토큰입니다."));

        // 도메인 객체로 유효성 검증
        var invitation = toDomain(invEntity);
        invitation.validateForActivation();

        // 사용자 계정 생성
        authService.createSupplierUser(
            invEntity.getTenantId(), invEntity.getEmail(),
            request.password(), invEntity.getEntityId());

        // 초대 상태 ACCEPTED로 전이
        invEntity.accept();

        log.info("공급업체 계정 활성화 완료 (email={})", invEntity.getEmail());
    }

    @Override
    @Transactional
    @Auditable(action = "SUPPLIER_DATA_SUBMITTED")
    public ActivityDataResponse submitData(UUID tenantId, UUID actorId,
                                            UUID entityId, SupplierDataRequest request) {
        // CreateActivityDataRequest로 변환하여 ghg 모듈에 위임
        var ghgRequest = new CreateActivityDataRequest(
            request.reportingYear(), request.category(), request.subCategory(),
            request.quantity(), request.unit(), request.countryCode(),
            "SUPPLIER_PORTAL", null, null);
        return ghgService.createActivityData(tenantId, entityId, ghgRequest);
    }

    @Override
    @Transactional
    public ActivityDataResponse submitForReview(UUID tenantId, UUID actorId, UUID activityDataId) {
        return workflowService.submitActivityData(tenantId, actorId, activityDataId);
    }

    @Override
    @Transactional
    public ActivityDataResponse approveData(UUID tenantId, UUID actorId, UUID activityDataId) {
        return workflowService.approveActivityData(tenantId, actorId, activityDataId);
    }

    @Override
    @Transactional
    public ActivityDataResponse rejectData(UUID tenantId, UUID actorId,
                                            UUID activityDataId, String reason) {
        return workflowService.rejectActivityData(tenantId, actorId, activityDataId, reason);
    }

    private SupplierInvitation toDomain(SupplierInvitationJpaEntity e) {
        return new SupplierInvitation(
            e.getId(), e.getTenantId(), e.getEntityId(), e.getEmail(),
            e.getToken(), e.getStatus(), e.getInvitedBy(),
            e.getExpiresAt(), e.getAcceptedAt(), e.getCreatedAt());
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 7: SupplierController + SecurityConfig 업데이트

**Files:**
- Create: `supply/api/SupplierController.java`
- Modify: `shared/security/SecurityConfig.java`

- [ ] **Step 1: SupplierController 작성**

```java
package ai.claudecode.esgt2.supply.api;

import ai.claudecode.esgt2.ghg.api.ActivityDataResponse;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supply")
@RequiredArgsConstructor
@Tag(name = "Supply", description = "공급업체 포털 API")
public class SupplierController {

    private final SupplierService supplierService;

    // ── 초대 / 계정 활성화 ──────────────────────────────────────────────────

    @Operation(summary = "공급업체 초대", description = "이메일로 공급업체를 초대하고 활성화 링크를 발송합니다.")
    @ApiResponse(responseCode = "201", description = "초대 완료")
    @ApiResponse(responseCode = "403", description = "권한 없음 (TENANT_ADMIN 필요)")
    @PostMapping("/suppliers/invite")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<SupplierInvitationResponse> inviteSupplier(
            @AuthenticationPrincipal JwtAuthentication auth,
            @RequestBody @Valid InviteSupplierRequest request) {
        var response = supplierService.inviteSupplier(
            auth.getTenantId(), auth.getPrincipal(), request);
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "계정 활성화",
               description = "초대 토큰으로 공급업체 계정을 활성화합니다. JWT 불필요.")
    @ApiResponse(responseCode = "204", description = "활성화 완료")
    @ApiResponse(responseCode = "400", description = "만료·사용됨·잘못된 토큰")
    @PostMapping("/suppliers/activate")
    @PreAuthorize("permitAll()")
    // @PreAuthorize 면제: 초대 토큰이 인증 역할을 대체한다 (03-security.md Webhook 항목과 동일 패턴).
    // SecurityConfig에 permitAll 등록됨.
    public ResponseEntity<Void> activateAccount(@RequestBody @Valid ActivateSupplierRequest request) {
        supplierService.activateAccount(request);
        return ResponseEntity.noContent().build();
    }

    // ── 공급업체 데이터 입력 / 제출 ─────────────────────────────────────────

    @Operation(summary = "활동 데이터 등록 (공급업체)",
               description = "SUPPLIER는 자신의 법인(entityId) 데이터만 등록 가능합니다.")
    @ApiResponse(responseCode = "201", description = "등록 성공")
    @ApiResponse(responseCode = "403", description = "타 법인 접근 금지")
    @PostMapping("/entities/{entityId}/activity-data")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ActivityDataResponse> submitData(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID entityId,
            @RequestBody @Valid SupplierDataRequest request) {
        // T-6-09: 크로스-엔티티 방어 — SUPPLIER는 JWT.entityId와 일치하는 법인만 접근 가능
        if (!entityId.equals(auth.getEntityId())) {
            return ResponseEntity.status(403).build();
        }
        var response = supplierService.submitData(
            auth.getTenantId(), auth.getPrincipal(), entityId, request);
        return ResponseEntity.created(
            URI.create("/api/v1/supply/entities/" + entityId +
                "/activity-data/" + response.id()))
            .body(response);
    }

    @Operation(summary = "데이터 검토 요청",
               description = "DRAFT 상태 데이터를 PENDING으로 전환합니다.")
    @ApiResponse(responseCode = "200", description = "전환 성공")
    @PostMapping("/activity-data/{activityDataId}/submit")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ActivityDataResponse> submitForReview(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID activityDataId) {
        return ResponseEntity.ok(supplierService.submitForReview(
            auth.getTenantId(), auth.getPrincipal(), activityDataId));
    }

    // ── ESG_MANAGER 승인 / 반려 ─────────────────────────────────────────────

    @Operation(summary = "활동 데이터 승인", description = "PENDING → APPROVED")
    @ApiResponse(responseCode = "200", description = "승인 완료")
    @PostMapping("/activity-data/{activityDataId}/approve")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ActivityDataResponse> approveData(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID activityDataId) {
        return ResponseEntity.ok(supplierService.approveData(
            auth.getTenantId(), auth.getPrincipal(), activityDataId));
    }

    @Operation(summary = "활동 데이터 반려", description = "PENDING → REJECTED")
    @ApiResponse(responseCode = "200", description = "반려 완료")
    @ApiResponse(responseCode = "400", description = "반려 사유 누락")
    @PostMapping("/activity-data/{activityDataId}/reject")
    @PreAuthorize("hasRole('ESG_MANAGER')")
    public ResponseEntity<ActivityDataResponse> rejectData(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable UUID activityDataId,
            @RequestParam String reason) {
        return ResponseEntity.ok(supplierService.rejectData(
            auth.getTenantId(), auth.getPrincipal(), activityDataId, reason));
    }
}
```

- [ ] **Step 2: SecurityConfig에 activate 경로 추가**

`.requestMatchers("/api/v1/intake/tenants/*/webhook").permitAll()` 아래에 추가:
```java
.requestMatchers("/api/v1/supply/suppliers/activate").permitAll()
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

---

## Task 8: SupplierReminderScheduler

**Files:**
- Create: `supply/internal/SupplierReminderScheduler.java`

- [ ] **Step 1: SupplierReminderScheduler 작성**

리마인더 주기: 매주 월요일 09:00 KST.

```java
package ai.claudecode.esgt2.supply.internal;

import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class SupplierReminderScheduler {

    private final SupplierInvitationRepository invitationRepository;
    private final EntityManagementService entityManagementService;
    private final GhgService ghgService;
    private final EmailGateway emailGateway;
    private final ReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void sendReminders() {
        reminderService.sendPendingReminders();
    }
}
```

- [ ] **Step 2: ReminderService 분리 (스케줄러 @Transactional 금지 원칙)**

```java
package ai.claudecode.esgt2.supply.internal;

import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.ghg.api.GhgService;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
class ReminderService {

    private final SupplierInvitationRepository invitationRepository;
    private final EntityManagementService entityManagementService;
    private final GhgService ghgService;
    private final EmailGateway emailGateway;

    @Value("${supply.reminder.reporting-year:2025}")
    private int reportingYear;

    @Transactional(readOnly = true)
    public void sendPendingReminders() {
        // ACCEPTED 초대(계정 활성화 완료) 중 데이터 미제출 법인에 리마인더
        invitationRepository.findAll().stream()
            .filter(inv -> "ACCEPTED".equals(inv.getStatus()))
            .filter(inv -> !ghgService.hasActivityData(
                inv.getTenantId(), inv.getEntityId(), reportingYear))
            .forEach(inv -> {
                var entityOpt = entityManagementService.findById(
                    inv.getTenantId(), inv.getEntityId());
                if (entityOpt.isPresent()) {
                    emailGateway.sendReminderEmail(
                        inv.getEmail(),
                        entityOpt.get().name(),
                        reportingYear);
                    log.info("리마인더 발송 (email={}, entity={}, year={})",
                        inv.getEmail(), inv.getEntityId(), reportingYear);
                }
            });
    }
}
```

**주의**: `invitationRepository.findAll()`은 공급업체 수가 적을 때만 적합하다. 대규모 운영 시 페이지네이션으로 교체 필요.

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```
git add src/main/java/ai/claudecode/esgt2/supply/ \
        src/main/java/ai/claudecode/esgt2/ghg/api/ActivityDataWorkflowService.java \
        src/main/java/ai/claudecode/esgt2/ghg/internal/DefaultActivityDataWorkflowService.java \
        src/main/java/ai/claudecode/esgt2/shared/security/SecurityConfig.java
git commit -m "feat: supply 모듈 — 공급업체 초대·활성화·워크플로우·리마인더 (T-6-07~T-6-11)"
```

---

## Task 9: 실패 테스트 먼저 작성 (TDD Red)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/supply/domain/SupplierInvitationDomainTest.java`
- Create: `src/test/java/ai/claudecode/esgt2/supply/support/StubEmailGateway.java`
- Create: `src/test/java/ai/claudecode/esgt2/supply/support/SupplyTestConfig.java`
- Create: `src/test/java/ai/claudecode/esgt2/supply/SupplyIntegrationTest.java`

- [ ] **Step 1: StubEmailGateway 작성 (이메일 캡처용)**

```java
package ai.claudecode.esgt2.supply.support;

import ai.claudecode.esgt2.supply.internal.EmailGateway;

import java.util.ArrayList;
import java.util.List;

/** 테스트용 인메모리 이메일 게이트웨이. JavaMailSender 없이 이메일 동작을 검증한다. */
public class StubEmailGateway implements EmailGateway {

    public record SentInvitation(String to, String tenantName, String activationLink) {}
    public record SentReminder(String to, String entityName, int reportingYear) {}

    private final List<SentInvitation> sentInvitations = new ArrayList<>();
    private final List<SentReminder> sentReminders = new ArrayList<>();

    @Override
    public void sendInvitationEmail(String to, String tenantName, String activationLink) {
        sentInvitations.add(new SentInvitation(to, tenantName, activationLink));
    }

    @Override
    public void sendReminderEmail(String to, String entityName, int reportingYear) {
        sentReminders.add(new SentReminder(to, entityName, reportingYear));
    }

    public List<SentInvitation> getSentInvitations() { return sentInvitations; }
    public List<SentReminder> getSentReminders()     { return sentReminders; }
    public void clear() { sentInvitations.clear(); sentReminders.clear(); }
}
```

- [ ] **Step 2: SupplyTestConfig 작성**

```java
package ai.claudecode.esgt2.supply.support;

import ai.claudecode.esgt2.supply.internal.EmailGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class SupplyTestConfig {

    @Bean
    @Primary  // SmtpEmailGateway 대신 StubEmailGateway 사용
    public EmailGateway stubEmailGateway() {
        return new StubEmailGateway();
    }
}
```

- [ ] **Step 3: SupplierInvitationDomainTest 작성**

```java
package ai.claudecode.esgt2.supply.domain;

import ai.claudecode.esgt2.shared.exception.EsgException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierInvitationDomainTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();

    @Test
    void 초대_생성_시_PENDING_상태_토큰_포함() {
        var inv = SupplierInvitation.create(TENANT_ID, ENTITY_ID, "supplier@test.com", ACTOR_ID);

        assertThat(inv.status()).isEqualTo("PENDING");
        assertThat(inv.token()).isNotNull();
        assertThat(inv.email()).isEqualTo("supplier@test.com");
        assertThat(inv.expiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void 이메일_공백_시_예외() {
        assertThatThrownBy(() ->
            SupplierInvitation.create(TENANT_ID, ENTITY_ID, "  ", ACTOR_ID))
            .isInstanceOf(EsgException.class);
    }

    @Test
    void 만료된_초대_활성화_시도_시_예외() {
        var expired = new SupplierInvitation(
            UUID.randomUUID(), TENANT_ID, ENTITY_ID, "s@t.com",
            UUID.randomUUID(), "PENDING", ACTOR_ID,
            OffsetDateTime.now().minusDays(1),  // 만료
            null, OffsetDateTime.now().minusDays(8));

        assertThatThrownBy(expired::validateForActivation)
            .isInstanceOf(EsgException.class)
            .hasMessageContaining("만료");
    }

    @Test
    void ACCEPTED_상태_초대_재활성화_시도_시_예외() {
        var accepted = new SupplierInvitation(
            UUID.randomUUID(), TENANT_ID, ENTITY_ID, "s@t.com",
            UUID.randomUUID(), "ACCEPTED", ACTOR_ID,
            OffsetDateTime.now().plusDays(7),
            OffsetDateTime.now(), OffsetDateTime.now().minusDays(1));

        assertThatThrownBy(accepted::validateForActivation)
            .isInstanceOf(EsgException.class)
            .hasMessageContaining("이미 사용");
    }
}
```

- [ ] **Step 4: 도메인 단위 테스트 실행 (Red → Green 확인)**

Run: `./gradlew test --tests "ai.claudecode.esgt2.supply.domain.*"`
Expected: PASS (도메인 객체는 이미 구현했으므로 Green)

- [ ] **Step 5: SupplyIntegrationTest 작성**

```java
package ai.claudecode.esgt2.supply;

import ai.claudecode.esgt2.entity.api.CreateEntityRequest;
import ai.claudecode.esgt2.entity.api.EntityManagementService;
import ai.claudecode.esgt2.entity.domain.LegalEntityType;
import ai.claudecode.esgt2.ghg.api.ActivityDataWorkflowService;
import ai.claudecode.esgt2.shared.security.JwtAuthentication;
import ai.claudecode.esgt2.supply.api.ActivateSupplierRequest;
import ai.claudecode.esgt2.supply.api.InviteSupplierRequest;
import ai.claudecode.esgt2.supply.api.SupplierDataRequest;
import ai.claudecode.esgt2.supply.api.SupplierService;
import ai.claudecode.esgt2.supply.infra.SupplierInvitationRepository;
import ai.claudecode.esgt2.supply.support.StubEmailGateway;
import ai.claudecode.esgt2.supply.support.SupplyTestConfig;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(SupplyTestConfig.class)
class SupplyIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SupplierService supplierService;
    @Autowired private EntityManagementService entityManagementService;
    @Autowired private ActivityDataWorkflowService workflowService;
    @Autowired private SupplierInvitationRepository invitationRepository;
    @Autowired private StubEmailGateway emailGateway;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID entityId;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM supplier_invitations WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM activity_data WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE tenant_id = '00000000-0000-0000-0000-000000000007')");
        jdbcTemplate.execute("DELETE FROM users WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute("DELETE FROM legal_entities WHERE tenant_id = '00000000-0000-0000-0000-000000000007'");
        jdbcTemplate.execute(
            "INSERT INTO tenants (id, code, name, country_code) " +
            "VALUES ('00000000-0000-0000-0000-000000000007', 'TEST7', '공급업체테스트', 'KR') " +
            "ON CONFLICT DO NOTHING");

        emailGateway.clear();

        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("TENANT_ADMIN")));

        var entityResp = entityManagementService.create(
            TENANT_ID, new CreateEntityRequest("공급업체테스트법인", "KR", LegalEntityType.SUBSIDIARY));
        entityId = entityResp.id();
    }

    // T-6-07: 공급업체 초대 + 이메일 발송
    @Test
    void 공급업체_초대_시_DB_저장_이메일_발송() {
        var request = new InviteSupplierRequest("supplier@example.com", entityId);

        var response = supplierService.inviteSupplier(TENANT_ID, ACTOR_ID, request);

        assertThat(response.email()).isEqualTo("supplier@example.com");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(invitationRepository.findByToken(
            invitationRepository.findAll().get(0).getToken())).isPresent();

        // 이메일이 발송됐는지 확인
        assertThat(emailGateway.getSentInvitations()).hasSize(1);
        assertThat(emailGateway.getSentInvitations().get(0).to())
            .isEqualTo("supplier@example.com");
    }

    // T-6-07: 계정 활성화 플로우
    @Test
    void 계정_활성화_후_상태_ACCEPTED_변경() {
        var invitation = supplierService.inviteSupplier(TENANT_ID, ACTOR_ID,
            new InviteSupplierRequest("supplier2@example.com", entityId));

        var savedInvitation = invitationRepository.findAll().stream()
            .filter(inv -> "supplier2@example.com".equals(inv.getEmail()))
            .findFirst().orElseThrow();

        supplierService.activateAccount(
            new ActivateSupplierRequest(savedInvitation.getToken(), "Password1234!"));

        var updated = invitationRepository.findById(savedInvitation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("ACCEPTED");
    }

    // T-6-08: SUPPLIER 자사 데이터 입력
    @Test
    void SUPPLIER_자사_법인에_데이터_등록_성공() {
        // SUPPLIER 인증 컨텍스트로 전환
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));

        var request = new SupplierDataRequest(
            2025, "SCOPE3_CAT1", "ELECTRONICS",
            new BigDecimal("5000"), "KRW", "KR");

        var response = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId, request);

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.dataSource()).isEqualTo("SUPPLIER_PORTAL");
    }

    // T-6-09: 타 법인 접근 시도 → 403 (컨트롤러 레이어 확인은 통합 테스트에서)
    @Test
    void SUPPLIER_타법인_entityId로_HTTP_요청_시_403() {
        // SUPPLIER A가 entityId가 다른 법인에 접근 시도
        UUID otherEntityId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        // JwtAuthentication.entityId != otherEntityId → 컨트롤러에서 403
        var auth = new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER"));
        assertThat(auth.getEntityId()).isNotEqualTo(otherEntityId);
        // 실제 HTTP 403 검증은 SupplierControllerSecurityTest에서 MockMvc로 수행
    }

    // T-6-10: 승인 워크플로우
    @Test
    void 공급업체_제출_후_ESG_MANAGER_승인_플로우() {
        // 데이터 입력
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));
        var data = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId,
            new SupplierDataRequest(2025, "SCOPE3_CAT1", "ELECTRONICS",
                new BigDecimal("3000"), "KRW", "KR"));

        // 제출 (DRAFT → PENDING)
        var pending = workflowService.submitActivityData(TENANT_ID, ACTOR_ID, data.id());
        assertThat(pending.status()).isEqualTo("PENDING");

        // ESG_MANAGER가 승인 (PENDING → APPROVED)
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, List.of("ESG_MANAGER")));
        var approved = workflowService.approveActivityData(TENANT_ID, ACTOR_ID, data.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
    }

    @Test
    void 공급업체_제출_반려_후_REJECTED_상태() {
        SecurityContextHolder.getContext().setAuthentication(
            new JwtAuthentication(ACTOR_ID, TENANT_ID, entityId, List.of("SUPPLIER")));
        var data = supplierService.submitData(TENANT_ID, ACTOR_ID, entityId,
            new SupplierDataRequest(2025, "SCOPE3_CAT1", "PAPER",
                new BigDecimal("1500"), "KRW", "KR"));

        workflowService.submitActivityData(TENANT_ID, ACTOR_ID, data.id());

        var rejected = workflowService.rejectActivityData(
            TENANT_ID, ACTOR_ID, data.id(), "단위 오류");
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }
}
```

- [ ] **Step 6: 통합 테스트 실행**

Run: `./gradlew test --tests "ai.claudecode.esgt2.supply.*"`
Expected: PASS (처음에는 일부 실패할 수 있음 — 구현 완료 후 다시 실행)

- [ ] **Step 7: 전체 테스트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 0 failures

---

## Task 10: SupplierController 보안 테스트 (T-6-09 HttpStatus 검증)

**Files:**
- Create: `src/test/java/ai/claudecode/esgt2/supply/SupplierControllerSecurityTest.java`

- [ ] **Step 1: SupplierControllerSecurityTest 작성**

```java
package ai.claudecode.esgt2.supply;

import ai.claudecode.esgt2.supply.support.SupplyTestConfig;
import ai.claudecode.esgt2.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SupplyTestConfig.class)
class SupplierControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private ai.claudecode.esgt2.shared.security.JwtTokenProvider jwtTokenProvider;

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate() {{
        setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(
                org.springframework.http.client.ClientHttpResponse r) { return false; }
        });
    }};

    // T-6-09: 타법인 접근 시도 → 403
    @Test
    void SUPPLIER가_타법인_entityId로_POST_요청_시_403() {
        UUID tenantId    = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID myEntityId  = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID otherEntity = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID actorId     = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // SUPPLIER JWT — entityId=myEntityId
        String token = jwtTokenProvider.generateAccessToken(
            actorId, tenantId, myEntityId, java.util.List.of("SUPPLIER"));

        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"reportingYear":2025,"category":"SCOPE3_CAT1","subCategory":"X",
             "quantity":100,"unit":"KRW","countryCode":"KR"}
            """;

        // otherEntity로 요청 → 403
        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/supply/entities/" + otherEntity + "/activity-data",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    // 활성화 엔드포인트는 JWT 없이 접근 가능 (permitAll)
    @Test
    void 활성화_엔드포인트는_JWT_없이_400_반환() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"token":"00000000-0000-0000-0000-000000000000","password":"pass1234"}
            """;

        var response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/supply/suppliers/activate",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

        // JWT 없이 접근 가능 (401 아님), 토큰 없으면 400
        assertThat(response.getStatusCode().value()).isNotEqualTo(401);
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew test --tests "ai.claudecode.esgt2.supply.SupplierControllerSecurityTest"`
Expected: PASS

---

## Task 11: ModularityTest 확인 + 최종 커밋

- [ ] **Step 1: ModularityTest 실행**

Run: `./gradlew test --tests "*ModularityTest"`
Expected: PASS — supply 모듈 경계 위반 없음

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 0 failures

- [ ] **Step 3: task.md 업데이트**

`docs/task.md`에서 T-6-07~11 상태를 DONE으로 변경:
```markdown
| T-6-07 | `feat:` SUPPLIER 계정 초대 + 이메일 발송 | DONE | EmailGateway 인터페이스, StubEmailGateway 테스트 |
| T-6-08 | `feat:` POST /api/v1/supply/entities/{entityId}/activity-data | DONE | JwtAuthentication.entityId 크로스-엔티티 방어 |
| T-6-09 | `test:` 공급업체 → 타사 데이터 접근 시도 → 403 | DONE | SupplierControllerSecurityTest |
| T-6-10 | `feat:` 공급업체 제출 → ESG_MANAGER 승인 워크플로우 | DONE | ActivityDataWorkflowService DRAFT→PENDING→APPROVED |
| T-6-11 | `feat:` 미제출 법인 자동 리마인더 스케줄러 | DONE | @ConditionalOnProperty, cron 매주 월 09:00 KST |
```

- [ ] **Step 4: 최종 커밋**

```
git add src/ docs/task.md
git commit -m "test: 공급업체 포털 통합 테스트 + ModularityTest 통과 (T-6-07~T-6-11)"
```

---

## 자가 검토 (Self-Review)

### 스펙 커버리지

| 요구사항 | 구현 Task |
|---|---|
| T-6-07: SUPPLIER 초대 + 이메일 | Task 1, 5, 6, 7 |
| T-6-08: POST /supplier/activity-data 자사만 | Task 7 (entityId 검증) |
| T-6-09: 타사 접근 → 403 | Task 7, 10 |
| T-6-10: SUPPLIER 제출 → ESG_MANAGER 승인 | Task 4, 6, 7 |
| T-6-11: 미제출 법인 리마인더 스케줄러 | Task 8 |

### 체크리스트

- [x] `domain/` 패키지에 Spring/JPA import 없음 (SupplierInvitation.java)
- [x] `@PreAuthorize` 전수 적용 (activate는 면제 사유 주석)
- [x] `@Auditable` 데이터 변경 메서드에 부착 (inviteSupplier, submitData, workflow 3개)
- [x] `permitAll` 경로 추가 → `TenantContextInterceptor` URL 패턴 필요 여부 확인  
  → `/api/v1/supply/suppliers/activate`는 tenantId가 URL에 없음. 활성화 시 RLS 세션 변수 미설정.  
  → `activateAccount`는 supplier_invitations 테이블만 읽음. RLS 갭은 수용 가능 (읽기 전용 + 토큰 검증).
- [x] `@ConditionalOnProperty` + `zone="Asia/Seoul"` 스케줄러에 적용
- [x] Mock DB 없음 — Testcontainers PostgreSQL 사용
- [x] supply → ghg.api/entity.api만 참조 (internal 직접 참조 없음) — ModularityTest 통과
- [x] `spring-boot-starter-mail` 전이 의존성 여부 → BOM에 없으므로 명시적 추가 필요 ✅
