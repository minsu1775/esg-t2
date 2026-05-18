# ADR-004: 외부 검증 워크스페이스(VW) MVP 격상 결정

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

esg-t1에서 외부 검증인 지원은 M+1(MVP 이후) 기능으로 계획되었다. 그러나 KSSB 2 의무화 이후 실무에서는 공시 보고서에 대한 외부 인증이 사실상 의무화될 전망이다. 외부 검증 기능 없이는 실제 기업에 판매하기 어렵다.

---

## 결정

**외부 검증 워크스페이스(VW 모듈)를 MVP에 포함한다.**

### 핵심 설계 결정

#### 1. 불변 스냅샷 (Immutable Snapshot)

검증 시점의 데이터가 검증 후 변경되면 검증의 의미가 없다. **보고서 승인(APPROVED) 후 생성된 스냅샷은 어떤 방법으로도 수정 불가**:

```sql
-- PostgreSQL 트리거로 DB 레벨 보호
CREATE RULE no_update_snapshot AS ON UPDATE TO verification_snapshots DO INSTEAD NOTHING;
CREATE RULE no_delete_snapshot AS ON DELETE TO verification_snapshots DO INSTEAD NOTHING;
```

도메인 레이어에서도 이중 방어:
```java
public void update() {
    throw new ImmutableSnapshotException("Verification snapshot cannot be modified");
}
```

#### 2. VERIFIER 역할 격리

VERIFIER는 지정된 스냅샷의 데이터만 열람 가능. 다른 보고서, 다른 테넌트의 데이터 접근 불가:

```sql
-- RLS 정책
CREATE POLICY verifier_snapshot_isolation ON verification_snapshots
    FOR SELECT TO verifier_role
    USING (id = current_setting('app.verifier_snapshot_id')::UUID);
```

#### 3. 모든 검증인 행동 AuditLog 기록

검증 과정의 신뢰성 보장을 위해 검증인의 모든 행동(@Auditable):
- 스냅샷 열람 시각
- 코멘트 작성
- 증거 문서 요청
- 최종 서명

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| M+1으로 유지 | 개발 기간 단축 | 규제 준수 기업에 미판매 | 시장 요건 미충족 |
| 외부 검증 솔루션 연계 | 구현 불필요 | 통합 복잡성, 데이터 외부 유출 위험 | 보안 리스크 |
| MVP 내 VW 모듈 구현 | 풀스택 솔루션, 데이터 통제권 | 개발 범위 증가 | **선택** |

---

## 결과

- 외부 감사법인이 시스템에 직접 접근하여 검증 수행 가능
- 스냅샷 불변성으로 검증 후 데이터 조작 방지
- VERIFIER 계정 격리로 다른 테넌트 데이터 보호

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 |
