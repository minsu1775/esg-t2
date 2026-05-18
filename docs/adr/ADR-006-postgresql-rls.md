# ADR-006: 단일 PostgreSQL + Row-Level Security로 테넌트 격리

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

esg-t2는 다수 테넌트(기업)의 ESG 데이터를 한 시스템에서 처리한다.
테넌트 간 데이터 격리는 보안·규정 준수의 핵심 요건이다.
격리 방식으로 세 가지 접근이 검토되었다: DB 분리, 스키마 분리, RLS.

---

## 결정

**단일 PostgreSQL 인스턴스 + Row-Level Security(RLS) 정책으로 테넌트 격리를 구현한다.**

### RLS 구현 원칙

1. 모든 핵심 테이블에 `tenant_id UUID NOT NULL` 컬럼 필수
2. 매 요청마다 `TenantContextInterceptor`가 `SET LOCAL app.current_tenant_id = '{id}'` 실행
3. RLS 정책: `USING (tenant_id = current_setting('app.current_tenant_id')::UUID)`
4. 애플리케이션 레벨 이중 검증(서비스 레이어 tenantId 일치 확인)을 RLS와 병행 적용

```sql
-- 핵심 테이블 공통 RLS 패턴
ALTER TABLE emission_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON emission_records
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
```

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| 테넌트별 별도 DB | 완전한 격리, 독립 스케일링 | 연결 풀 관리 복잡, 운영 비용 N배 증가, 스키마 마이그레이션 N회 실행 필요 | MVP에 과도한 운영 복잡성 |
| 스키마 분리(Schema-per-tenant) | DB보다 간단, 격리 수준 높음 | Flyway 멀티 스키마 관리 복잡, 연결 수 증가, 동적 스키마 생성 필요 | Flyway + Spring Data JPA 통합 복잡성 |
| RLS (선택) | 단일 Flyway 마이그레이션, 연결 풀 단순, Spring Data JPA 그대로 사용 | 세션 변수 미설정 시 전체 노출 위험 | **선택** — 위험은 이중 방어로 완화 |

---

## 위험 완화

### 세션 변수 미설정 방어

`TenantContextInterceptor`에서 `current_tenant_id` 미설정 시 RLS가 무효화될 수 있다.
이를 방지하기 위한 이중 방어:

1. **DB 레벨**: `app.current_tenant_id` 미설정 시 `current_setting()` 오류 발생 → 전체 쿼리 실패
2. **애플리케이션 레벨**: 서비스 레이어에서 조회 결과 `tenantId` == JWT tenantId 명시적 검증

```java
// GlobalExceptionHandler에 필수 등록
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    return ResponseEntity.status(403).body(ErrorResponse.of("ACCESS_DENIED", "접근이 거부되었습니다."));
}
```

### VERIFIER 추가 격리

VERIFIER 역할은 테넌트 내에서도 지정된 snapshot_id 외 접근 불가.
`app.verifier_snapshot_id` 세션 변수를 추가하여 2단계 RLS 적용.

---

## 결과

- 단일 Flyway 마이그레이션 경로 — 스키마 변경이 N테넌트 동시 적용
- Spring Data JPA Repository 코드 변경 없음 — RLS가 DB 레벨에서 자동 필터
- 테넌트별 성능 격리 필요 시 M+1에서 Connection Pool 분리 검토 가능
- esg-t1에서 RLS 패턴 검증 완료

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 (ChatGPT 1차 리뷰 A-3 반영) |
