# ESG 공시지원 시스템 esg-t2 — 버그 수정 이력 (Fix)

> **목적**: 개발 중 발생한 버그의 원인·영향·TDD 해결 과정을 추적한다.  
> **포맷**: 각 버그는 식별자(BUG-P{Phase}-{번호})로 관리  
> **레퍼런스**: esg-t1/docs/fix.md (계승 교훈 → insight.md 참조)

---

## 버그 추적 포맷

```markdown
### BUG-P{X}-{NN}: 제목

- **발견 Phase**: Phase X
- **발견 일시**: YYYY-MM-DD
- **심각도**: CRITICAL / HIGH / MEDIUM / LOW
- **증상**: 어떤 동작이 잘못되었는지 (관찰된 현상)
- **원인**: 코드/설계상 근본 원인
- **영향**: 어느 기능/데이터에 영향을 미쳤는지
- **해결 방법**: TDD 절차 (Red → Green → Refactor)
  - `test:` 버그를 재현하는 실패 테스트 작성
  - `feat:` 수정 구현
  - `refactor:` 코드 정리 (필요시)
- **재발 방지**: 이 버그를 막을 수 있는 설계/규칙
```

---

## Phase 0: 프로젝트 셋업

_버그 발생 시 추가 예정_

---

## Phase 1: 법인·테넌트 관리

_버그 발생 시 추가 예정_

---

## Phase 2: AuditLog & Hash Chain

_버그 발생 시 추가 예정_

---

## Phase 3: 배출계수 & Scope 1/2

_버그 발생 시 추가 예정_

---

## Phase 4: 다법인 연결 집계

_버그 발생 시 추가 예정_

---

## Phase 5: Scope 3 계산

### T-5R-01 [P2 · FIXED] Cat.1 데이터 품질 자동 결정 미적용

**증상**: `Scope3Cat1Calculator.deriveDataQuality()` 메서드가 단위 테스트에서만 검증되고, `ActivityData.create()` 흐름에서 호출되지 않음. Cat.1 활동 데이터 등록 시 `dataQuality`가 항상 `AVERAGE_DATA`(기본값)로 저장됨.  
**원인**: T-5-03 구현 시 계산기 메서드는 작성됐으나 `ActivityData.create()` 내 카테고리 분기 로직 누락.  
**수정**: `ActivityData.create()` — `"SCOPE3_CAT1"` 카테고리 감지 후 `Scope3Cat1Calculator.deriveDataQuality(dataSource)` 호출. 회귀 테스트 3건 추가.

### T-5R-02 [P3 · FIXED] `Scope3CoverageRequest.@NotNull int` primitive 유효성 검사 무효

**증상**: `@NotNull int reportingYear` — `int` primitive에 `@NotNull`은 컴파일만 되고 Bean Validation 런타임에 아무 효과 없음.  
**수정**: `@NotNull` → `@Min(2020) @Max(2030)` 으로 교체.

### T-5R-03 [P3 · FIXED] `Scope3CoverageCalculator` 카테고리 목록 순서 비결정적

**증상**: `includedCategories`/`excludedCategories` 저장 시 `HashMap.keySet()` 순서가 JVM 실행마다 다를 수 있음. JSON `[2,1]` vs `[1,2]` 불일치로 재현성 저하.  
**수정**: `.stream().sorted().toList()`로 오름차순 정렬 보장.

### T-5R-04 [P3 · FIXED] `DefaultScope3Service` Cat.1/Cat.2 도메인 계산기 우회

**증상**: `calculateScope3()` 내부에서 `Scope3Cat1/2Calculator.computeEmission()` 대신 `EmissionCalculator.computeEmission()` 직접 호출. 나중에 Cat.1/Cat.2 계산 로직이 바뀌면 서비스에 반영되지 않을 위험.  
**수정**: `computeEmission(ad, factor, scope3CategoryNum)` 메서드 내 `switch`로 카테고리별 계산기 경유.

---

## Phase 6: 데이터 수집 파이프라인

_버그 발생 시 추가 예정_

---

## Phase 7: 공시 보고서 생성

_버그 발생 시 추가 예정_

---

## Phase 8: 외부 검증 워크스페이스

_버그 발생 시 추가 예정_

---

## Phase 9~11: 프론트엔드

_버그 발생 시 추가 예정_

---

## Phase 12: 통합 검증

_버그 발생 시 추가 예정_

---

## 버그 통계 (자동 집계 — Phase 12 종료 후)

| Phase | CRITICAL | HIGH | MEDIUM | LOW | 합계 |
|---|---|---|---|---|---|
| 전체 | - | - | - | - | - |
