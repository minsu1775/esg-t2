# ADR-008: Hash Chain 무결성 오류 발생 시 재난 복구(DR) 절차

- **상태**: Accepted
- **날짜**: 2026-05-19
- **결정자**: esg-t2 기획팀
- **참조**: ADR-002 (AuditLog & Hash Chain), ADR-007 (Append-only)

---

## 배경

AuditLog Hash Chain은 모든 데이터 변경 이력이 SHA-256 해시 체인으로 연결된 구조다.
각 레코드의 `current_hash = SHA-256(previous_hash + canonical_payload)`로 계산된다.

운영 중 다음 상황에서 체인 무결성 오류가 발생할 수 있다:

1. **DB 직접 수정** — DBA 또는 배포 스크립트가 `audit_logs` 테이블을 직접 UPDATE/DELETE
2. **해시 계산 로직 버그** — `canonicalPayload()` 구현 변경으로 기존 해시와 불일치
3. **인코딩 이슈** — 문자셋 변경 또는 직렬화 순서 변경으로 재계산 시 해시 불일치
4. **데이터베이스 복구** — 백업에서 특정 시점 복원 후 이후 레코드와 체인 단절

무결성 오류 발생 시 "무엇이 변경되었는가"를 판별하고, 이후 레코드의 신뢰성을 어떻게 처리할지 명확한 절차가 없으면 감사 대응이 불가능하다.

---

## 결정

**Hash Chain 오류 발생 시 다음 3단계 절차를 따른다: 격리 → 분석 → 봉인.**

재계산(recalculation)으로 체인을 "수선"하는 행위는 절대 금지한다. 오류 발생 지점을 투명하게 기록하고 이후 체인을 새로 시작한다.

---

## 무결성 오류 감지 방법

### 자동 감지 (운영 스케줄러)

```java
// HashChainVerificationScheduler — 매일 새벽 2시 KST
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
public void verifyHashChainIntegrity() {
    List<AuditLogEntity> logs = auditLogRepository.findAllOrderById();
    String previousHash = null;
    for (AuditLogEntity log : logs) {
        String expected = hashChainCalculator.compute(previousHash, log.canonicalPayload());
        if (!expected.equals(log.getCurrentHash())) {
            publisher.publishEvent(new HashChainIntegrityViolatedEvent(
                log.getId(), log.getTenantId(), previousHash, log.getCurrentHash()
            ));
            break; // 첫 번째 오류 발견 후 중단
        }
        previousHash = log.getCurrentHash();
    }
}
```

### 수동 감지

```bash
# 전체 체인 검증 CLI (Phase 12에서 구현)
./gradlew bootRun --args="--verify-hash-chain --tenant-id=<UUID>"
```

---

## 복구 절차

### Step 1: 격리 (즉시, 오류 감지 후 30분 이내)

```sql
-- 오류 발생 테넌트의 신규 데이터 쓰기 차단 (app_user 권한 임시 회수)
REVOKE INSERT ON audit_logs FROM app_user WHERE tenant_id = '<오류 테넌트 UUID>';

-- 오류 발생 지점 이후 레코드 조회 (삭제 금지 — 증거 보존)
SELECT id, tenant_id, action, actor_id, occurred_at, current_hash
FROM audit_logs
WHERE id >= '<오류 최초 레코드 ID>'
ORDER BY id;
```

> ⚠️ 오류 레코드 및 이후 레코드를 **절대 DELETE하지 않는다.** 오류 기록 자체가 감사 증거다.

### Step 2: 분석 (오류 감지 후 2시간 이내)

**2-A. 원인 분류**

| 원인 유형 | 판별 방법 | 대응 |
|---|---|---|
| DB 직접 수정 | `pg_audit` 로그에서 UPDATE/DELETE 이력 확인 | 보안 인시던트 처리 |
| 해시 로직 변경 | Git blame으로 `canonicalPayload()` 변경 이력 확인 | 코드 버그 분류 |
| 인코딩/직렬화 이슈 | 오류 레코드 payload를 수동으로 SHA-256 재계산 | 코드 버그 분류 |
| DB 복구 | 복구 시점 이후 레코드와 체인 비교 | 복구 범위 문서화 |

**2-B. 영향 범위 확정**

```sql
-- 오류 발생 시점부터 현재까지 영향 받은 레코드 수
SELECT COUNT(*) FROM audit_logs
WHERE occurred_at >= '<오류 최초 레코드 occurred_at>'
  AND tenant_id = '<오류 테넌트 UUID>';
```

### Step 3: 봉인 (복구 불가 체인은 공식 봉인 처리)

해시 체인을 "수선"하는 대신, 오류 지점을 공식 봉인하고 새 체인을 시작한다.

```sql
-- 봉인 레코드 삽입 (audit_logs에 CHAIN_BREAK 타입으로 INSERT)
INSERT INTO audit_logs (
    id, tenant_id, action, actor_id, entity_type, entity_id,
    occurred_at, previous_hash, current_hash, payload
) VALUES (
    gen_random_uuid(),
    '<오류 테넌트 UUID>',
    'CHAIN_BREAK_SEALED',                    -- 특수 액션 타입
    '<담당자 UUID>',
    'SYSTEM',
    NULL,
    NOW(),
    '<오류 직전 정상 레코드의 current_hash>',
    '<새 SHA-256: CHAIN_BREAK_SEALED + 봉인 사유>',
    '{"reason": "<원인>", "affected_from": "<ID>", "sealed_by": "<담당자>", "incident_ref": "<인시던트 번호>"}'
);
```

이후 레코드는 이 봉인 레코드의 해시를 `previous_hash`로 사용하여 체인을 계속 이어간다.

### Step 4: 보고 (봉인 후 24시간 이내)

1. **내부 보고**: 경영진 + 개발팀에 인시던트 리포트 전달
2. **외부 공시 대응**: 오류 기간에 생성된 보고서가 있다면 해당 보고서에 `INTEGRITY_CAVEAT` 플래그 추가
3. **감사인 통보**: 진행 중인 외부 검증 워크스페이스가 있다면 VERIFIER에게 즉시 통보

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| 체인 재계산 (recalculate) | 오류 흔적 없음 | 원본 변조와 구별 불가, 감사 시 신뢰 훼손 | **절대 금지** |
| 오류 레코드 삭제 후 재삽입 | 체인 복구 가능 | INSERT-only 원칙 위반, 법적 책임 | ADR-007 위반 |
| 봉인(CHAIN_BREAK_SEALED) + 새 체인 시작 | 투명성 유지, 감사 대응 가능 | 오류 이후 기간 신뢰도 저하 | **선택** |

---

## 예방 조치

1. **PostgreSQL `pg_audit` 활성화**: `audit_logs` 테이블 DDL/DML 전수 감사 로그
2. **DB 계정 권한 분리**: `app_user`는 INSERT만, UPDATE/DELETE는 DB 관리자만 (ADR-007)
3. **배포 스크립트 검토 체크리스트**: `audit_logs` 직접 수정 여부 확인 항목 포함
4. **매일 자동 검증**: `HashChainVerificationScheduler` — 오류 발생 후 최대 24시간 이내 감지

---

## 결과

- Hash Chain 무결성 오류는 "수선" 없이 투명하게 기록하고 봉인한다
- 봉인 레코드(`CHAIN_BREAK_SEALED`)가 오류 발생 증거로서 영구 보존된다
- 감사인은 봉인 전 구간과 봉인 후 구간을 분리하여 신뢰도를 평가할 수 있다
- `HashChainVerificationScheduler`가 오류를 24시간 내 자동 감지한다

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-19 | 초안 작성 (Gemini 리뷰 제안 3 반영) |
