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

### BUG-P6-01 [P1 · FIXED] CSV 파싱 오류 → 500 반환

- **발견 Phase**: Phase 6 재검토 (2026-05-20)
- **심각도**: HIGH
- **증상**: 필수 헤더 누락·파싱 불가 CSV 업로드 시 400 대신 **500** 반환. 클라이언트가 오류 원인 파악 불가.
- **원인**: `CsvActivityDataParser.parse()`가 `IllegalArgumentException`을 던지고, `GlobalExceptionHandler`의 `Exception.class` 핸들러가 500으로 처리.
- **영향**: CSV 업로드 API에서 잘못된 파일을 보내는 모든 클라이언트.
- **해결**: `DefaultIntakeService.uploadCsv()`에서 `IllegalArgumentException` → `EsgException(CSV_PARSE_FAILED)` 래핑. `GlobalExceptionHandler`의 기존 `CSV_PARSE_FAILED → 400` 매핑이 동작.
- **재발 방지**: domain 객체가 던지는 예외는 service 계층에서 EsgException으로 변환.

### BUG-P6-02 [P1 · FIXED] Webhook RLS 컨텍스트 미설정

- **발견 Phase**: Phase 6 재검토 (2026-05-20)
- **심각도**: HIGH
- **증상**: JWT 없는 Webhook 요청에서 `TenantContextInterceptor`가 `auth == null` 조건으로 즉시 반환 → `app.current_tenant_id` 미설정 → 운영 환경 RLS 무력화.
- **원인**: `TenantContextInterceptor`가 SecurityContext의 인증 객체만 읽고, `permitAll` 경로(Webhook)에서는 SecurityContext가 비어 있음. MVC Interceptor는 Security Filter 이후 실행되므로, 컨트롤러에서 SecurityContext를 설정해도 이미 인터셉터는 지나간 상태.
- **영향**: 운영 환경에서 Webhook 수신 시 RLS 정책이 활성화된 테이블에 대한 쿼리가 잘못 동작할 수 있음. (테스트 환경은 `db/migration-pg` 미실행으로 RLS 미활성화 — 테스트에서 감지 불가)
- **해결**: `TenantContextInterceptor`에 Webhook URL 패턴(`/api/v1/intake/tenants/{tenantId}/webhook`) fallback 추가. URL에서 tenantId를 추출해 `set_config` 호출.
- **재발 방지**: `permitAll` 경로가 추가될 때마다 TenantContextInterceptor에서 RLS 컨텍스트 설정 여부 확인.

### BUG-P6-03 [P1 · FIXED] entityId 테넌트 소속 미검증

- **발견 Phase**: Phase 6 재검토 (2026-05-20)
- **심각도**: HIGH
- **증상**: CSV 업로드(`entityId` 경로 파라미터) 및 Webhook(`item.entityId()`) 모두 테넌트에 속하지 않는 법인 ID를 검증하지 않고 데이터를 저장.
- **원인**: Phase 4 `DefaultConsolidationService`에 적용한 `EntityManagementService.findById(tenantId, entityId)` 패턴이 Intake 경로에 미적용.
- **영향**: 존재하지 않거나 다른 테넌트 소속의 법인 ID로 활동 데이터가 저장 가능 → FK 무결성 오류 또는 데이터 오염.
- **해결**: `DefaultIntakeService.uploadCsv()`에서 단건 entityId 검증. `receiveWebhook()`에서 unique entityId 일괄 검증 후 미소속 법인이 있으면 `EsgException(VALIDATION_FAILED)` throw.
- **재발 방지**: 새 데이터 수집 경로 추가 시 entityId 검증을 첫 번째 단계로 명시.

### BUG-P6-04 [P2 · FIXED] CsvActivityDataParser — domain 패키지에서 Spring Resource 의존

- **발견 Phase**: Phase 6 재검토 (2026-05-20)
- **심각도**: MEDIUM
- **증상**: `domain/` 패키지 규칙("순수 Java") 위반. `import org.springframework.core.io.Resource` 포함.
- **원인**: 초기 구현 시 편의를 위해 Spring `Resource`를 직접 받도록 설계.
- **해결**: `parse(Resource)` → `parse(InputStream)`으로 시그니처 변경. 호출 측(`DefaultIntakeService`)에서 `csvFile.getInputStream()` 전달.

### BUG-P6-05 [P3 · FIXED] 음수 quantity 검증 없음

- **발견 Phase**: Phase 6 재검토 (2026-05-20)
- **심각도**: LOW
- **증상**: CSV/Webhook에서 음수 quantity를 입력해도 ERROR 없이 DB에 저장됨.
- **해결**: `ActivityDataRowImporter.importRow()`에서 `row.quantity().signum() <= 0` 검증 추가 → ERROR 반환.

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
