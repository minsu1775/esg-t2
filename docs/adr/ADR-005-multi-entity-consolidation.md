# ADR-005: 다법인 연결 집계 방식 설계

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

KSSB 2 의무화 대상(연결자산 30조+)은 사실상 모든 대기업 그룹사다. 이들은 수십 개의 자회사·관계회사를 보유하며, ESG 공시는 연결 기준으로 제출해야 한다. esg-t1의 단일 법인 모델로는 상용화 불가.

GHG Protocol과 KSSB 2는 두 가지 연결 경계 설정 방법을 허용한다:
- **Equity Method (지분율 기준)**: 보유 지분율만큼 배출량 귀속
- **Operational Control (운영 지배력)**: 운영 통제권이 있으면 100% 귀속

---

## 결정

**두 가지 연결 방법을 모두 지원하되, 법인별로 선택 가능한 구조로 설계한다.**

### 핵심 알고리즘

#### Equity Method
```
ConsolidatedEmission(parent) = Σ(
    EntityEmission(child) × DirectOwnership(parent, child)
) + Σ(
    ConsolidatedEmission(sub-child) × IndirectOwnership(parent, sub-child)
)
```

#### 이중 계상 제거

그룹사 내 거래(Intra-group transaction)로 인한 중복 집계 방지:
- 동일 법인이 여러 경로로 포함되는 경우: 최상위 직접 경로만 적용
- DAG(Directed Acyclic Graph) 탐색으로 순환 참조 탐지

#### 순환 참조 방지

지분율 관계는 DAG여야 한다. 사이클 감지:
```java
public void validateNoCycle(EntityRelationshipGraph graph) {
    // DFS 방문 체크로 사이클 탐지
    // 사이클 발견 시 CircularOwnershipException 발생
}
```

### DB 설계

`entity_relationships` 테이블:
```sql
effective_from, effective_to: 지분율 변경 이력 관리
ownership_ratio: 0.0001~1.0000 (소수점 4자리)
```

지분율 변경 시 재계산 트리거:
- 지분율 변경 → 해당 보고 연도 연결 배출량 재계산 이벤트 발행

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| Equity Method만 지원 | 단순 | Operational Control 미지원 시 일부 기업 부적합 | 기능 부족 |
| Operational Control만 지원 | 단순 | 관계회사(지분 50% 미만) 처리 불가 | 기능 부족 |
| 두 방법 모두 지원 | 규제 완전 충족 | 구현 복잡성 증가 | **선택** |

---

## 결과

- 대기업 그룹사 연결 공시 요건 완전 충족
- 지분율 변경 이력 관리로 과거 연도 재계산 가능
- 이중 계상 제거 알고리즘으로 정확한 연결 수치 보장

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 |
