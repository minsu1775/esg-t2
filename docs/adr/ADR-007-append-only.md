# ADR-007: Append-only / INSERT-only 데이터 전략

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

ESG 공시 데이터는 규제 기관과 외부 감사인이 과거 시점의 데이터를 그대로 재현할 수 있어야 한다.
KSSB 2(IFRS S2 수렴)는 데이터 변경 이력의 완전한 추적 가능성(traceability)을 요구한다.

esg-t1에서 `UPDATE` 방식으로 데이터를 수정할 경우 두 가지 문제가 발생했다:
1. Hash Chain 무결성 검증 실패 — 이전 해시 기반으로 체인이 구성되어 있는데 원본 데이터가 변경되면 불일치
2. 감사인 재현 불가 — "공시 제출 시점의 데이터"를 복원할 수 없음

---

## 결정

**배출량 계산에 사용된 핵심 데이터는 INSERT-only로 관리한다. 정정은 새 버전 INSERT로 처리한다.**

### 적용 테이블

| 테이블 | 전략 | 근거 |
|---|---|---|
| `activity_data` | INSERT-only (정정 시 새 버전) | 배출량 계산 원천 데이터 |
| `emission_records` | INSERT-only (재계산 시 새 레코드) | 계산 결과 재현성 |
| `audit_logs` | INSERT-only (절대 변경 금지) | Hash Chain 무결성 |
| `verification_snapshots` | INSERT-only (불변) | 감사인 열람 데이터 |
| `disclosure_reports` | INSERT-only (재공시 시 새 버전) | 공시 이력 추적 |

### DB 권한 제한

```sql
-- app_user 역할에서 핵심 테이블 UPDATE/DELETE 권한 박탈
REVOKE UPDATE, DELETE ON activity_data FROM app_user;
REVOKE UPDATE, DELETE ON emission_records FROM app_user;
REVOKE UPDATE, DELETE ON audit_logs FROM app_user;
REVOKE UPDATE, DELETE ON verification_snapshots FROM app_user;
```

### 정정(Correction) 패턴

```java
// 정정은 새 버전 INSERT — 원본 레코드 불변
@Auditable(action = "ACTIVITY_DATA_CORRECTED")
public ActivityDataId correct(CorrectActivityDataCommand cmd) {
    // 1. 원본 레코드 조회 (불변)
    ActivityData original = repository.findById(cmd.originalId());
    // 2. 정정 버전 생성 (새 ID, original_id 참조 포함)
    ActivityData corrected = original.correct(cmd);
    // 3. INSERT only
    return repository.save(corrected).id();
}
```

### `Snapshot.state` 유일한 허용 변경

스냅샷의 `state` 컬럼은 `ACTIVE → ARCHIVED` 전이만 허용한다.
이는 비즈니스 상태 변경이며 데이터 내용 변경과 다르다.

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| UPDATE 방식 | 스토리지 효율, 단순한 조회 | 이력 소실, Hash Chain 불일치, 재현 불가 | 규제 요건 위반 |
| 이력 테이블 분리 (audit shadow table) | 최신 데이터와 이력 분리 | 이중 쓰기 복잡성, 동기화 위험 | esg-t1에서 동기화 버그 경험 |
| INSERT-only + 버전 컬럼 (선택) | 단일 테이블, 재현 가능, Hash Chain 정합 | 스토리지 증가 (수용 가능 수준) | **선택** |

---

## 결과

- `DataPointVersion`, `CalculationResult`, `AuditLog` — INSERT only, DB 권한으로 UPDATE/DELETE 차단
- Snapshot에 FormulaVersion ID + EmissionFactor 버전 기록 → 과거 계산 100% 재현 가능
- 감사인이 임의 시점 데이터를 요청하면 당시 버전 ID로 즉시 재현
- 스토리지 비용 증가: 활동 데이터 건당 ~500B 추정, 연 10만 건 기준 50MB — 수용 가능

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 (ChatGPT 1차 리뷰 A-4 반영) |
