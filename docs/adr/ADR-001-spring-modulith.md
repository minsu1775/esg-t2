# ADR-001: Spring Modulith를 이용한 모듈 경계 강제

- **상태**: Accepted
- **날짜**: 2026-05-18
- **결정자**: esg-t2 기획팀

---

## 배경

esg-t2는 단일 Spring Boot 애플리케이션 내에 6개의 비즈니스 도메인(ghg, entity, vw, rpt, supply, audit)을 가진다. 모놀리식 코드베이스에서 모듈 간 경계를 강제하지 않으면 장기적으로 의존성이 뒤엉켜 변경 비용이 급증한다.

esg-t1에서 Modular Monolith 원칙을 선언했으나, 컴파일 타임이나 테스트 타임에 위반을 감지하는 메커니즘이 없었다.

---

## 결정

**Spring Modulith 2.0.x를 도입하여 컴파일 타임 + 테스트 타임 모듈 경계를 강제한다.**

### 모듈 규칙

1. 각 모듈의 `api/` 패키지만 외부 모듈이 접근 가능
2. `internal/` 패키지는 동일 모듈 내부에서만 접근 가능
3. 모듈 간 비동기 통신: `ApplicationEventPublisher` → `@ApplicationModuleListener`
4. 직접 Repository 크로스 참조 금지

```java
// src/test/java/ai/claudecode/esgt2/ModularityTest.java — CI에서 항상 실행
// @ApplicationModuleTest 는 단일 모듈 슬라이스 테스트용 — 전체 경계 검증에는 사용 금지
class ModularityTest {
    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(Esgt2Application.class).verify();
    }
}
```

---

## 대안 검토

| 대안 | 장점 | 단점 | 탈락 이유 |
|---|---|---|---|
| 마이크로서비스 | 강한 격리, 독립 배포 | 분산 시스템 복잡성, 운영 비용 급증 | 팀 규모·초기 단계에 과도한 복잡성 |
| 순수 컨벤션(명명 규칙) | 도입 비용 없음 | 위반 감지 불가, esg-t1에서 실패 확인 | 효과 없음 검증됨 |
| Spring Modulith | 컴파일+테스트 타임 강제, 이벤트 기반 통신 지원 | 러닝 커브 | **선택** |

---

## 결과

- `ModularityTest`가 실패하면 빌드 실패 → 경계 위반 즉시 감지
- 모듈 간 의존성 문서 자동 생성 (Spring Modulith Documenter)
- 미래 마이크로서비스 분리 시 모듈 경계가 서비스 경계가 됨

---

## 검토 이력

| 날짜 | 내용 |
|---|---|
| 2026-05-18 | 초안 작성 |
