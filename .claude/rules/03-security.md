---
name: security
description: RBAC @PreAuthorize, RLS, 크로스테넌트 방어, 파일 업로드 보안, JWT
---

# 보안 규칙

## @PreAuthorize — 인가 없는 엔드포인트 금지

모든 REST 컨트롤러 메서드에 `@PreAuthorize` 적용 필수. 누락 시 코드 리뷰 차단.

```java
@PreAuthorize("hasRole('ESG_MANAGER')")
@PostMapping("/activity-data")
public ResponseEntity<ActivityDataResponse> create(...) { ... }

@PreAuthorize("hasRole('VERIFIER') and @snapshotSecurity.canAccess(#snapshotId)")
@GetMapping("/vw/snapshots/{snapshotId}")
public ResponseEntity<SnapshotResponse> getSnapshot(...) { ... }
```

## RBAC 역할 계층

| 역할 | 주요 권한 |
|---|---|
| `SUPER_ADMIN` | 전체 |
| `TENANT_ADMIN` | 법인 관리, AuditLog 조회 |
| `ESG_MANAGER` | 활동 데이터 입력·승인, 보고서 생성 |
| `ESG_VIEWER` | 조회만 |
| `VERIFIER` | 지정 스냅샷만 접근, 검증 서명 |
| `SUPPLIER` | 자사 데이터만 입력 |

## Row-Level Security (PostgreSQL RLS)

모든 핵심 테이블에 RLS 정책 적용. 애플리케이션 레벨 + DB 레벨 이중 방어.

```sql
-- 세션 변수 설정 (요청마다 필터 인터셉터에서)
SET LOCAL app.current_tenant_id = '...';

-- 정책 예시
CREATE POLICY tenant_isolation ON emission_records
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

VERIFIER 전용 RLS: `WHERE snapshot_id = current_setting('app.verifier_snapshot_id')`

## 크로스 테넌트 방어

서비스 레이어에서 조회 결과의 `tenantId`와 현재 인증 컨텍스트 `tenantId` 일치 검증.
RLS만으로는 부족 — 애플리케이션 레벨 이중 체크 필수.

## AccessDeniedException → 403 (esg-t1 L-0-10)

`GlobalExceptionHandler`에 반드시 명시적 등록. 미처리 시 Spring 기본 **500** 반환.

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    return ResponseEntity.status(403)
        .body(ErrorResponse.of("ACCESS_DENIED", "접근이 거부되었습니다."));
}
```

## 파일 업로드 보안 (esg-t1 BUG-P3-09)

### 경로 순회 방어

```java
public Path resolveContained(Path storageRoot, String filename) {
    Path resolved = storageRoot.resolve(filename).normalize();
    if (!resolved.startsWith(storageRoot)) {
        throw new EsgException(EsgErrorCode.INVALID_FILE_PATH);
    }
    return resolved;
}
```

### 파일명 처리

- 저장 시 파일명 → UUID로 재생성 (원본명은 `original_filename` 컬럼에 보관)
- 확장자 허용 목록 (allowlist): `pdf, xlsx, xls, csv, png, jpg, jpeg`
- 목록 외 확장자 → 즉시 거부 (400)

## SQL Injection 방어

사용자 입력 → 쿼리 연결 절대 금지. 반드시 PreparedStatement / JPQL 파라미터 바인딩.

```java
// ❌ 금지
String query = "SELECT * FROM tenants WHERE name = '" + name + "'";

// ✅ 필수
@Query("SELECT t FROM TenantEntity t WHERE t.name = :name")
Optional<TenantEntity> findByName(@Param("name") String name);
```

## API 응답 민감 데이터 노출 금지

응답 DTO에 비밀번호·토큰·개인식별정보 포함 금지.
`@JsonIgnore` 또는 별도 응답 DTO 사용.

## JWT 보안

- Access Token: 15분, Refresh Token: 7일
- 로그아웃 시 Refresh Token Redis 블랙리스트 등록
- MVP: HMAC-SHA256 자체 서명, `NimbusJwtDecoder.withSecretKey(secretKey)` 사용
- M+1 Keycloak 전환 시: `NimbusJwtDecoder.withJwkSetUri(url)`로 빈 교체만 하면 됨
- `JwtDecoder` 빈을 `@Configuration`으로 추상화하여 전환 코드 최소화

## Webhook 서명 검증

외부 Webhook 수신 시 HMAC-SHA256 서명 검증 필수. 불일치 → 401.

```java
String computed = HmacUtils.hmacSha256Hex(secret, payload);
if (!MessageDigest.isEqual(computed.getBytes(), signature.getBytes())) {
    throw new UnauthorizedException("Invalid webhook signature");
}
```
