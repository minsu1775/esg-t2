# ChatGPT 1차 리뷰 검토 분석 — 2026-05-18

> **원본 파일**: `docs/codexreviews/esg_t_2_full_document_review_2026_05_18.md`  
> **검토자**: Claude Sonnet 4.6  
> **검토 기준**: 현재 실제 문서 상태와 1:1 대조 후 판단  
> **작성일**: 2026-05-18

---

## 범례

| 상태 | 의미 |
|---|---|
| ✅ **적용** | 즉시 문서/설계에 반영해야 할 타당한 지적 |
| ⚠️ **조건부 적용** | 방향은 맞으나 MVP 이후 단계에서 반영 |
| ❌ **부적용** | 과도한 복잡성, MVP 범위 외, 설계 철학 불일치 |
| 📋 **이미 반영됨** | 현재 문서/설계에 이미 존재하여 조치 불필요 |
| 🚫 **오류 정보** | ChatGPT가 실제 문서를 잘못 파악한 경우 |

---

## 1. 오류 정보 — ChatGPT가 잘못 파악한 것

이 항목들은 ChatGPT가 문서를 오래된 버전 또는 불완전하게 읽어 발생한 오류다.
**적용 불필요, 다만 인지는 필요.**

### 1-1. "plan.md가 거의 비어 있다" 🚫

> 원문: "현재 plan.md는 거의 비어 있습니다. 이건 프로젝트 전체에서 가장 큰 문제 중 하나입니다."

**실제 상태**: plan.md는 Phase 0~12 전체 상세 구현 계획이 작성되어 있다. 각 Phase별 목표·DB 마이그레이션·태스크 목록이 포함됨. ChatGPT가 저장소의 오래된 버전 또는 다른 파일을 참조한 것으로 보임.

**조치**: 불필요.

---

### 1-2. "ADR이 약하다. 왜 Modulith인가 등을 ADR로 남겨야 한다" 🚫

> 원문: "반드시 ADR로 남겨야 할 것: 왜 Modulith인가, 왜 RLS인가..."

**실제 상태**: ADR-001(Spring Modulith 선택 이유·모듈 경계 검증), ADR-002(@Auditable AOP), ADR-003(Scope3 카테고리 선정), ADR-004(VW MVP 격상), ADR-005(다법인 연결 방식)가 이미 존재한다.

**단, 부분 누락 확인**: "왜 RLS인가", "왜 INSERT-only인가"는 ADR로 없음 → **별도 항목 2-3에서 처리**.

---

### 1-3. "계산 엔진 설계가 없다" 🚫

> 원문: "Formula Engine, Calculation Graph, Versioned Formula, Recalculation Strategy, Incremental Calculation 설계가 없습니다."

**실제 상태**:
- `.claude/rules/06-emission-calculation.md` — BigDecimal 전용, resolveAt() 재현성, UnitConverter, Scope3 95% 임계값, 데이터 품질 등급 상세 설계
- `.claude/rules/07-formula-dsl.md` — Lexer→Parser→Evaluator 아키텍처, DoS 방어 상수, YAML 포맷, test_cases 게이트, 버전 관리, Snapshot 재현성
- `docs/spec.md` — Formula DSL 전체 섹션 존재

**조치**: 불필요.

---

### 1-4. "CLAUDE.md가 너무 거대해질 위험이 있다" 🚫

> 원문: "CLAUDE.md가 철학·개발 가이드·운영 규칙·아키텍처 규칙·코드 규칙을 모두 포함하게 됩니다."

**실제 상태**: esg-t1의 거대 CLAUDE.md 문제(L-0-11 교훈)를 이미 반영. 현재 CLAUDE.md는 약 120줄로 슬림화되어 있으며, 실제 규칙은 `.claude/rules/` 11개 파일로 분리됨. 이미 ChatGPT가 제안한 구조가 구현되어 있음.

**조치**: 불필요.

---

### 1-5. "AI Hallucination 대응 전략이 부족하다" 🚫

> 원문: "AI가 잘못된 추론을 했을 때, 도메인을 오해했을 때 방어 전략이 부족합니다."

**실제 상태**: 이미 여러 메커니즘으로 대응:
- `docs/plan-review-01_2026-05-18.md` — 구현 전 전수 문서 검토 (43개 항목)
- `.claude/rules/02-testing.md` — TDD 강제(Red→Green→Refactor)로 잘못된 구현 즉시 탐지
- `docs/code-review.md` — Phase별 체크리스트 (수동 Human Approval Gate)
- `docs/fix.md` — 버그 발생 시 TDD 해결 추적

**조치**: 불필요.

---

## 2. 즉시 적용 — 문서에 반영해야 할 타당한 지적

### 2-1. spec.md에 PostgreSQL 버전 오탈자 ✅

**지적 없음 (이번 리뷰에서 자체 발견)**: spec.md 아키텍처 다이어그램 80번째 줄에 `PostgreSQL 17 (RLS 활성화)` 오탈자 존재. 다른 모든 위치는 PostgreSQL 18로 수정됐으나 이 부분만 누락.

**조치**: spec.md 즉시 수정.

---

### 2-2. 규칙 우선순위 체계(P0~P3) 추가 ✅

**지적 근거**: "규칙 충돌이 반드시 발생한다. 예: 성능 최적화 vs auditability, 단순성 vs 확장성, MVP 속도 vs immutability. 하지만 현재는 Rule Priority 개념이 부족하다."

**타당성**: AI Agent 개발에서 규칙이 충돌할 때 해소 기준이 없으면 임의 판단이 발생. 현재 .claude/rules/에 우선순위 명시 없음. 간단한 계층 정의만으로 충분.

**제안 내용**:

| 우선순위 | 규칙 | 이유 |
|---|---|---|
| P0 | 테넌트 격리 (RLS + 앱 레벨 이중 검증) | 데이터 침해 = 즉시 서비스 종료 |
| P0 | Audit 무결성 (Hash Chain, Immutable) | 규제 위반, 검증 실패 |
| P1 | 재현성 (factorAt, Formula Versioning) | 공시 번복 불가 |
| P2 | 성능·확장성 | 중요하나 P0/P1보다 낮음 |
| P3 | 개발 편의성 | 최후 고려 |

**조치**: CLAUDE.md 또는 `00-priority.md` 신규 파일에 추가.

---

### 2-3. ADR 2개 추가 (왜 RLS인가, 왜 INSERT-only인가) ✅

**지적 근거**: "왜 RLS인가, 왜 INSERT-only인가 ADR로 남겨야 한다." — 현재 ADR에 이 두 가지가 명시적으로 없음.

**타당성**: 두 결정은 구현 전반에 영향을 주는 핵심 아키텍처 선택이다. 향후 팀원이나 AI Agent가 "왜 UPDATE를 안 쓰지?"라고 의문을 가질 때 참조 지점이 필요함.

**조치**: ADR-006(Row-Level Security 채택 이유), ADR-007(Append-only / INSERT-only 전략) 작성.

---

### 2-4. spec.md에 Capacity Planning 섹션 추가 ✅

**지적 근거**: "하루 데이터량, Evidence 파일 크기, AuditLog 증가량 등 운영 관점 수치가 없다. ESG 시스템은 파일 시스템이 되어버리기 쉽다."

**타당성**: 볼륨 추정이 없으면 DB 파티셔닝, Object Storage 선택, Archive 전략 결정 근거가 없다. MVP 수준의 간단한 추정치라도 필요.

**조치**: spec.md에 "운영 규모 추정(MVP 기준)" 섹션 추가.

```
예시 항목:
- 테넌트 수: ~50개 (MVP 기준)
- 법인 수/테넌트: 평균 5~10개
- AuditLog 증가: 월 ~수십만 건 (MVP 규모)
- Evidence 파일 크기: 평균 5MB, 연간 ~10,000건/테넌트
- Snapshot 보관: 무기한 (규제 요건)
- AuditLog 보관: 10년 이상
```

---

### 2-5. supply 모듈 MVP 범위 명확화 ✅

**지적 근거**: "공급업체 포털은 생각보다 훨씬 어렵다. 실제로는 공급업체 인증, 다국어, 응답률 관리, 재요청, 온보딩, 보안 문제가 크다. 현재 범위는 MVP 치고 너무 크다."

**타당성**: 맞는 지적이다. 공급업체 포털의 실제 복잡성은 별도 SaaS 수준일 수 있다. 현재 prd.md에서 supply 모듈의 MVP 경계가 명확하지 않음.

**MVP 범위 권장**:
- ✅ 포함: 공급업체 데이터 CSV 업로드, 수동 승인, 기본 데이터 격리 (RLS)
- ✅ 포함: 공급업체 계정 생성 (SUPPLIER 역할)
- ❌ MVP 제외 → M+1: 공급업체 자체 포털 UI, 이메일 알림, 응답률 추적, 다국어

**조치**: prd.md F-06(공급업체 포털) 항목에 MVP 경계 명시 추가.

---

## 3. 조건부 적용 — MVP 이후 단계에서 반영

### 3-1. AuditLog 파티셔닝 / Archive 전략 ⚠️ (Phase 12 이후)

**지적 근거**: "AuditLog는 append only, 빠르게 증가, 조회 패턴 특수, 장기 보관 특성이 있다. 일반 업무 DB와 섞이면 성능 문제가 발생하기 쉽다."

**타당성**: 장기적으로 맞는 지적. 다만 MVP 규모(수십만 건/월)에서는 단일 PostgreSQL로 충분. PESSIMISTIC_WRITE, Outbox Pattern이 이미 설계됨.

**조치 시점**: Phase 12 성능 벤치마크에서 실측 후 결정. spec.md에 장기 전략 메모로만 추가.

---

### 3-2. Event Naming Convention / Retry Policy 강화 ⚠️ (Phase 3~5에서 자연 정의)

**지적 근거**: "Domain Event Catalog, Event Naming Convention, Retry Policy, Idempotency Strategy, Dead Letter Queue 정책이 필요하다."

**타당성**: 방향은 옳다. 현재 `11-modulith-events.md`에 이벤트 규칙이 있으나 Retry Policy, Dead Letter Queue는 없음.

**조치 시점**: Phase 3(GHG 계산)~Phase 5(Scope3)에서 실제 이벤트가 구현되면 그때 자연스럽게 정의. 지금 문서화하면 추측에 기반한 설계가 됨.

---

### 3-3. Security Architecture 강화 (Secret Management, Key Rotation) ⚠️ (Phase 12)

**지적 근거**: "Tenant Isolation, Object Storage Security, Signed URL, Key Rotation, Secret Management, Audit Access Policy가 필요하다."

**타당성**: 운영 환경에서는 모두 필요. 현재 `03-security.md`에 RBAC, RLS, Webhook 서명 등이 있으나 Key Rotation, Secret Management는 없음.

**조치 시점**: Phase 12 보안 감사 단계에서 반영. 현재는 개발 인프라 설계 단계.

---

### 3-4. 배치 전략 (Spring Batch / Chunk Processing) ⚠️ (Phase 5~6 판단)

**지적 근거**: "Scope3는 실제로는 배치 시스템이다. Job Scheduler, Batch Retry, Chunk Processing, Backpressure, Long-running Job Tracking이 필요하다."

**타당성**: Scope3 공급망 데이터 규모에 따라 맞을 수 있다. 현재 CSV 대량 업로드는 `REQUIRES_NEW` 행별 독립 트랜잭션으로 설계됨(05-async-concurrency.md). 충분한지는 실측 필요.

**조치 시점**: Phase 5(Scope3) 구현 시 데이터 볼륨 확인 후 Spring Batch 도입 여부 결정.

---

### 3-5. 모듈별 계약 문서화 ⚠️ (코드 작성 시 자연 정의)

**지적 근거**: "각 모듈마다 Owned Aggregate, Published Events, Internal Events, Allowed Dependencies, External Contract, Persistence Ownership을 정의해야 한다."

**타당성**: 완성 단계에서 유용한 문서. 다만 지금 정의하면 코드 없이 추측으로 작성되어 실제 코드와 괴리가 생길 위험이 큼.

**조치 시점**: Phase 2(audit) 완료 시점부터 구현된 모듈에 한해 문서화 시작.

---

## 4. 부적용 — 반영하지 않아야 할 제안

### 4-1. .claude/rules 디렉터리 재구조화 ❌

**지적 근거**: "architecture/, backend/, frontend/, security/, database/, testing/ 등으로 하위 디렉터리 구조화를 권장한다."

**부적용 이유**:
1. Claude Code의 `.claude/rules/` 로드 메커니즘은 flat file 기반. 하위 디렉터리의 파일이 자동 로드되는지 보장 없음
2. 현재 01~11 숫자 prefix로 로드 순서와 적용 범위(`paths:`)를 제어. 이미 효과적으로 분리됨
3. 순수 리팩토링이며 기능 향상 없음. YAGNI 원칙 위배

---

### 4-2. ABAC(Attribute Based Access Control) 도입 ❌

**지적 근거**: "ESG_MANAGER가 너무 많은 권한을 가진다. RBAC 대신 tenant_id, entity_id, department_id, disclosure_year, data_sensitivity, approval_state 기반 ABAC로 전환해야 한다."

**부적용 이유**:
1. 현재 RBAC + PostgreSQL RLS 이중 방어로 테넌트 격리 충분
2. ABAC 도입은 인증 인프라 전체 재설계를 요구. MVP 복잡성이 수배 증가
3. Spring Security의 `@PreAuthorize` + SpEL로 속성 기반 접근 제어의 일부는 이미 가능 (예: `@snapshotSecurity.canAccess(#snapshotId)`)
4. M+2 이후 실제 고객 요구사항 확인 후 결정이 적절

---

### 4-3. Regulation Engine 도입 (다국적 지원) ❌

**지적 근거**: "CSRD(EU), SEC Climate Rule, 일본 SSBJ 등 지역별 규제 차이를 위한 Regulation Engine이 필요하다. disclosure framework, taxonomy mapping, jurisdiction, applicability rule 개념 도입 권장."

**부적용 이유**:
1. esg-t2 MVP는 명확히 한국(KSSB 1/2, IFRS S1/S2) 중심
2. Regulation Engine은 별도 SaaS 제품 수준의 복잡성
3. 다국적 지원은 실제 글로벌 고객 확보 후 설계하는 것이 올바른 순서
4. 글로벌 확장 시 쓰기 좋은 구조는 이미 확보됨 (emission_factors.standard, formula YAML의 gwp: AR6 등)

---

### 4-4. insight.md에 제품 전략(ICP, 산업별 전략) 추가 ❌

**지적 근거**: "ICP(이상 고객), 산업별 전략, 중견기업 vs 대기업 전략, 컨설팅사 연계 전략을 추가해야 한다."

**부적용 이유**:
1. insight.md는 **기술적 학습 인사이트** 문서 (esg-t1 교훈 L-0-01~L-0-16 계승)
2. 제품/마케팅 전략은 별도 영역. 현재 프로젝트는 학습·기술 검증 목적도 강함
3. prd.md에 이해관계자 및 시장 컨텍스트가 이미 정의됨

---

### 4-5. MVP 축소 권장 (Scope3·VW·Multi-Entity 제거) ❌

**지적 근거**: "Scope3, Supplier Portal, Verification Workspace, Multi-Entity를 동시에 하려는 것은 위험하다. 진짜 MVP는 Multi-Entity + Scope1/2 + Audit + Snapshot + Evidence + Basic Report만 포함해야 한다."

**부적용 이유**:
1. **설계 결정 이미 완료**. ADR-003(Scope3), ADR-004(VW MVP 격상), ADR-005(Multi-Entity)에 근거가 명시됨
2. KSSB 2 의무 공시 요건상 Scope3 Cat.1 없이는 규제 적합성 없음 (ADR-003 참조)
3. VW 없이는 외부 검증 대응 불가 — 실제 기업 판매 불가 (ADR-004 참조)
4. Multi-Entity 없이는 연결 자산 30조+ 대기업 타겟 불가 (ADR-005 참조)
5. Phase 0~12 단계별 구현으로 병렬 개발이 아닌 순차 구축임

---

### 4-6. 단일 PostgreSQL DB 분리 (업무 DB / Audit DB / Object Metadata) ❌

**지적 근거**: "단일 PostgreSQL에 멀티테넌트 + AuditLog + Snapshot + Evidence Metadata + Scope3 대량 데이터를 모두 처리하려는 것은 성장 시 위험하다."

**부적용 이유**:
1. MVP 규모(수십만 건/월)에서 단일 PostgreSQL 18로 충분
2. DB 분리는 분산 트랜잭션, 데이터 일관성, 운영 복잡도를 수배 증가시킴
3. PostgreSQL 18의 파티셔닝, TOAST, Partial Index로 대부분 해결 가능
4. Phase 12 실측 벤치마크 후 판단이 올바른 순서 (3-1 참조)

---

### 4-7. Metadata 독립 모듈화 ❌

**지적 근거**: "MetricDefinition, FormulaDefinition, DisclosureMapping, DataLineage, EvidenceReference, QualityRule를 독립 모듈로 구성해야 한다."

**부적용 이유**:
1. **이미 분산 구현됨**:
   - FormulaDefinition → `07-formula-dsl.md` + Formula DSL YAML
   - emission_factors → GHG 모듈
   - DataLineage → 증빙→ActivityData→EmissionRecord 추적 경로 (10-evidence-files.md)
   - DataQuality → `06-emission-calculation.md` SPEND_BASED/AVERAGE_DATA/SUPPLIER_SPECIFIC 등급
   - DisclosureMapping → `disclosure_schedules` + KSSB 2 섹션 구조
2. 별도 모듈 추가는 모듈 간 의존성 증가, Spring Modulith 경계 복잡화

---

## 5. 검토 대상 외 — 사업적 판단 영역

ChatGPT 리뷰에 포함된 다음 제안들은 **기술 문서 검토 범위를 벗어난 제품/사업 판단 영역**이다.

- "어떤 고객을 먼저 공략할지, 어떤 산업군에 집중할지" → 사업 전략
- "컨설팅사 연계 전략" → 파트너십 전략
- "중견기업 vs 대기업 전략" → 세일즈 전략
- "운영 Runbook (장애 대응, 백업 절차)" → Phase 12 이후 운영 문서

---

## 6. 즉시 적용 액션 플랜

| 번호 | 작업 | 대상 파일 | 우선순위 |
|---|---|---|---|
| A-1 | spec.md 아키텍처 다이어그램 `PostgreSQL 17` → `PostgreSQL 18` 수정 | `docs/spec.md` | 즉시 |
| A-2 | 규칙 우선순위 체계 (P0~P3) 추가 | `CLAUDE.md` 또는 `.claude/rules/00-priority.md` | 이번 세션 |
| A-3 | ADR-006: 왜 단일 PostgreSQL + RLS인가 작성 | `docs/adr/ADR-006-postgresql-rls.md` | 이번 세션 |
| A-4 | ADR-007: 왜 Append-only / INSERT-only인가 작성 | `docs/adr/ADR-007-append-only.md` | 이번 세션 |
| A-5 | spec.md에 Capacity Planning 기초 추정치 섹션 추가 | `docs/spec.md` | 이번 세션 |
| A-6 | prd.md F-06 supply 모듈 MVP 경계 명확화 | `docs/prd.md` | 이번 세션 |

---

## 7. 종합 평가

ChatGPT 리뷰는 **ESG 도메인 이해도가 높고 운영 관점에서 유익한 시각**을 제공했다. 특히:

- **강점 평가는 정확**: immutability, auditability, traceability, 규제 중심 사고에 대한 높은 평가는 설계 방향이 맞음을 확인
- **운영 복잡도 경고는 유효**: AuditLog 증가, 공급업체 포털 복잡성, 배치 전략 부재는 Phase 12 전에 계획 필요

다만:

- **문서를 불완전하게 읽었음**: plan.md, 계산 엔진 규칙 파일들, ADR을 제대로 참조하지 못해 이미 해결된 문제를 지적하는 경우가 많았음
- **MVP 범위 축소 권장은 수용 불가**: 이미 ADR에 근거가 있는 설계 결정을 뒤집는 제안이며, KSSB 2 요건과 충돌함
- **일부 제안이 과도**: ABAC, Regulation Engine, DB 분리, rules 디렉터리 재구조화는 MVP 단계에서 복잡성 대비 효익이 없음

**결론**: A-1~A-6 액션을 실행하면 현재 문서의 남은 빈틈이 대부분 채워진다.
