# ADR-002: @Auditable AOP를 이용한 AuditLog 자동 기록

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

ESG 공시 데이터는 규제 기관과 감사인이 변경 이력을 검토한다. esg-t1에서 AuditTrail을 수동 호출 방식으로 구현했고, 신규 서비스 메서드에서 누락이 발생했다 (BUG-P6-02). 데이터 변경 누락은 외부 검증(VW 모듈)에서 신뢰성 문제로 이어진다.

---

## 결정

**모든 데이터 변경 메서드에 `@Auditable(action = "...")` 어노테이션을 부착하고, AOP Around Advice가 AuditLog를 자동으로 기록한다.**

```java
// 어노테이션 정의
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();           // 예: "DATA_POINT_CREATED"
    String entityType() default ""; // 자동 추론 가능하면 생략
}

// 사용 예시
@Auditable(action = "ACTIVITY_DATA_APPROVED")
public void approve(ActivityDataId id, UserId approverId) { ... }

// AuditAspect (audit 모듈 internal)
@Around("@annotation(auditable)")
public Object audit(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
    Object before = captureState(pjp);
    Object result = pjp.proceed();
    Object after = captureState(pjp);
    outboxService.publish(AuditEvent.of(auditable.action(), before, after));
    return result;
}
```

### DB Outbox Pattern과 결합

AuditLog 기록 실패가 비즈니스 트랜잭션을 롤백하면 안 된다. 반대로, 비즈니스 트랜잭션 성공 후 AuditLog 누락도 안 된다. **DB Outbox Pattern**으로 이 문제를 해결한다:

1. 비즈니스 트랜잭션 내에서 `outbox_events` 테이블에 이벤트 저장 (원자적)
2. Outbox Poller가 비동기로 이벤트 처리 → `audit_logs` 테이블 저장

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| 수동 호출 | 단순 | 누락 위험 (esg-t1에서 실패 확인) | 효과 없음 |
| Spring Data Auditing (@CreatedBy 등) | 자동화 | 변경 전/후 상태 비교 불가 | 기능 부족 |
| DB 트리거 | 애플리케이션 코드 불필요 | 비즈니스 맥락(actor, action) 기록 불가 | 정보 부족 |
| @Auditable AOP | 누락 방지, 맥락 포함, 테스트 가능 | AOP 복잡성 | **선택** |

---

## 결과

- 데이터 변경 메서드에 어노테이션 없으면 코드 리뷰에서 즉시 거부 (체크리스트 항목)
- 통합 테스트에서 AuditLog 생성 여부 항상 검증
- DB Outbox로 AuditLog 누락 원천 차단

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 |
