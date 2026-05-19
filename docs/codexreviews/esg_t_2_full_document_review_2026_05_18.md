# ESG 공시지원 시스템(esg-t2) 전체 문서 검토 보고서

## 대상 저장소
- 저장소: [minsu1775/esg-t2 GitHub Repository](https://github.com/minsu1775/esg-t2?utm_source=chatgpt.com)
- 검토 범위:
  - docs/prd.md
  - docs/spec.md
  - docs/plan.md
  - docs/regulatory.md (간접 참조)
  - docs/task.md
  - docs/adr
  - CLAUDE.md

---

# 1. 전체 총평 (Executive Summary)

esg-t2 프로젝트는 단순한 ESG 데이터 입력 시스템이 아니라:

- KSSB/IFRS S1/S2 기반 공시 플랫폼
- 다법인 연결 ESG 플랫폼
- Scope 3 중심 공급망 ESG 시스템
- 외부 검증(Assurance) 대응 플랫폼
- 향후 iXBRL 기반 디지털 공시 플랫폼

으로 확장 가능한 구조를 목표로 하고 있습니다.

특히 다음 부분은 매우 우수합니다.

## 강점

### 1. 규제 중심 사고가 강함
단순 기능 나열이 아니라:

- KSSB 1/2
- IFRS S1/S2
- GHG Protocol
- Scope 3
- Assurance
- Audit Trail
- Data Lineage

등 실제 상용 ESG 플랫폼에서 중요한 개념이 반영되어 있습니다.

이는 일반적인 CRUD 프로젝트 수준을 넘어:

"규제 기반 엔터프라이즈 플랫폼" 관점으로 설계되었다는 의미입니다.

---

### 2. 데이터 무결성 개념이 매우 좋음
다음 개념들은 상용 ESG/회계/감사 시스템에서 핵심입니다.

- Immutable Snapshot
- INSERT-only 전략
- Audit Hash Chain
- Evidence Traceability
- 재현성(Reproducibility)
- Version 기반 계산

이 부분은 오히려 일반 스타트업 수준보다 훨씬 성숙합니다.

---

### 3. MVP와 M+1 분리가 비교적 잘 되어 있음
많은 프로젝트가:

- 현재 해야 하는 것
- 미래에 필요한 것

을 섞어서 실패하는데,

현재 문서는:

- MVP
- M+1
- Phase2

구분이 비교적 명확합니다.

이는 실제 개발 가능성을 높입니다.

---

# 2. 프로젝트 전체에서 가장 큰 문제점

현재 문서의 가장 큰 문제는:

## “규제/데이터/감사 관점은 강하지만 운영 시스템 관점은 아직 약하다”

입니다.

즉:

- ESG 도메인 이해
- 규제 이해
- 공시 구조

는 상당히 좋지만,

실제 SaaS/엔터프라이즈 시스템에서 중요한:

- 운영 복잡도
- 멀티테넌시 위험
- 권한 폭발
- 데이터 볼륨
- 장기 유지보수
- 배치 처리
- 비용 구조
- 조직 운영
- 데이터 계약(Data Contract)
- 메타데이터 관리

관점은 아직 부족합니다.

이 프로젝트가 실제 운영 단계로 가면:

"ESG 계산 로직" 보다
"운영 복잡도"
때문에 더 어려워질 가능성이 큽니다.

---

# 2-1. 검토 범위 재정의 및 추가 검토 필요 사항

초기 검토에서는 다음 문서 중심으로 분석했습니다.

- docs/prd.md
- docs/spec.md
- docs/plan.md
- 일부 ADR 구조

하지만 사용자 지적대로 실제 프로젝트의 중요한 운영 철학과 AI Agent 규칙은:

- .claude/rules/**
- CLAUDE.md
- docs/insight.md
- docs/regulatory.md
- docs/spec.md 세부 항목
- docs/task.md
- ADR 상세 문서

에 더 많이 포함되어 있습니다.

특히 이 프로젝트는:

- AI Agent 기반 개발 프로세스
- Claude Code 규칙 기반 개발
- SDD(Spec Driven Development)
- 규제 기반 아키텍처

철학이 강하기 때문에,

단순 PRD/Spec 수준 검토만으로는 부족합니다.

---

# 추가 상세 검토 (CLAUDE.md / .claude/rules / insight / regulatory 중심)

---

# CLAUDE.md 검토

## 총평

CLAUDE.md는 단순한 AI assistant instruction 파일이 아니라:

# “프로젝트 운영 철학 문서”

역할을 하고 있습니다.

특히:

- SDD 기반 개발 흐름
- 문서 우선 개발
- 규칙 기반 AI 협업
- Architecture Governance
- 개발자 행동 표준화

관점이 강하게 반영되어 있습니다.

이는 상당히 좋은 방향입니다.

---

## 매우 좋은 부분

### 1. 문서 우선 개발 철학

현재 CLAUDE.md는:

- prompt
- spec
- plan
- task
- implementation

흐름을 강제하려고 합니다.

이건 AI Agent 시대에서 매우 중요한 접근입니다.

왜냐하면:

AI 기반 개발은:

"코드를 먼저 작성"

하면 거의 반드시:

- 일관성 붕괴
- 아키텍처 드리프트
- 규칙 충돌
- 품질 편차

가 발생하기 때문입니다.

---

### 2. 규칙 기반 개발 의도가 좋음

.claude/rules 와 연결되는 방향은 좋습니다.

특히:

- naming
- architecture
- modular boundary
- coding discipline

을 AI가 반복적으로 지키게 하는 것은 장기적으로 매우 중요합니다.

---

## 문제점

# 문제 1. CLAUDE.md가 너무 거대해질 위험

현재 방향대로 가면:

CLAUDE.md가:

- 철학
- 개발 가이드
- 운영 규칙
- 아키텍처 규칙
- 코드 규칙
- AI 행동 규칙

을 모두 포함하게 됩니다.

이건 유지보수 지옥이 될 가능성이 큽니다.

---

## 개선 방향

CLAUDE.md는:

# “AI 개발 운영 헌장”

수준만 유지해야 합니다.

그리고 실제 규칙은:

- .claude/rules/architecture
- .claude/rules/backend
- .claude/rules/security
- .claude/rules/testing
- .claude/rules/domain

등으로 분리하는 것이 좋습니다.

---

# 문제 2. 규칙 우선순위 체계가 없음

현재 가장 위험한 부분 중 하나.

AI Agent 기반 개발에서는:

규칙 충돌이 반드시 발생합니다.

예:

- 성능 최적화 vs auditability
- 단순성 vs 확장성
- MVP 속도 vs immutability

등.

하지만 현재는:

# Rule Priority

개념이 부족합니다.

---

## 반드시 추가 권장

예:

| 우선순위 | 규칙 |
|---|---|
| P0 | Tenant Isolation |
| P0 | Audit Integrity |
| P1 | Reproducibility |
| P2 | Performance |
| P3 | Developer Convenience |

---

# .claude/rules 디렉터리 검토

## 총평

이 프로젝트에서 사실상 가장 중요한 자산 중 하나입니다.

왜냐하면:

단순 코딩 규칙이 아니라:

# “AI Agent 행동 제약 시스템”

역할을 하기 때문입니다.

이는 매우 현대적인 접근입니다.

---

## 매우 좋은 부분

### 1. Architecture Drift 방지 의도

매우 좋습니다.

특히 AI Agent는:

- 즉흥 구현
- 임시 로직 추가
- Layer 우회
- Domain 침범

을 자주 수행합니다.

rules 기반 제약은 필수에 가깝습니다.

---

### 2. 규칙 기반 코드 생성 방향

장기적으로:

- onboarding 단축
- 코드 품질 표준화
- AI 품질 안정화

효과가 큽니다.

이 방향은 계속 강화하는 것이 좋습니다.

---

## 핵심 문제점

# 문제 1. Rule Taxonomy 부족

현재 규칙들이:

- coding rule
- architecture rule
- domain rule
- security rule

관점으로 체계화되어야 하는데,

혼재될 위험이 있습니다.

---

## 권장 구조

.claude/rules/

- architecture/
- backend/
- frontend/
- security/
- database/
- testing/
- observability/
- domain/
- ai-agent/
- governance/

---

# 문제 2. Rule Enforcement 전략이 없음

현재는:

"규칙 문서"

수준인데,

실제로 중요한 것은:

# 자동 검증

입니다.

---

## 반드시 필요

예:

- ArchUnit
- OpenRewrite
- Static Analysis
- Convention Test
- Modulith Verification

등.

즉:

문서 규칙이 아니라:

# Executable Governance

로 가야 합니다.

---

# 문제 3. AI Hallucination 대응 전략 부족

AI Agent 프로젝트에서 매우 중요.

현재 규칙은:

- 어떻게 구현할지

는 있지만:

- AI가 잘못된 추론을 했을 때
- 도메인을 오해했을 때
- 규제를 잘못 해석했을 때

방어 전략이 부족합니다.

---

## 추가 권장

반드시 추가:

- Human Approval Gate
- High-risk Change Review
- Regulatory Validation Rule
- Domain Expert Approval

---

# docs/insight.md 검토

## 총평

insight.md는:

단순 메모가 아니라:

# 프로젝트의 핵심 사고 자산

입니다.

특히:

- ESG 시장 구조
- 경쟁 우위
- 실제 pain point
- 공급망 문제
- 검증 문제

관점 통찰이 좋습니다.

---

## 매우 좋은 부분

### 1. "검증 대응"을 핵심 가치로 본 점

이건 상당히 중요합니다.

실제 ESG 시장에서:

많은 기업이 원하는 건:

"예쁜 대시보드"

가 아니라:

"감사 대응 가능성"

입니다.

이 관점은 계속 유지해야 합니다.

---

### 2. 공급망 데이터 품질 문제 인식

매우 현실적입니다.

실제 Scope3 프로젝트는:

계산보다:

- 데이터 누락
- 공급업체 응답 부족
- 품질 불일치

가 더 큰 문제입니다.

---

## 문제점

# 문제 1. 제품 전략으로 구체화되지 않음

insight는 훌륭하지만:

아직:

- 어떤 고객을 먼저 공략할지
- 어떤 pain point를 MVP로 해결할지
- 어떤 산업군에 집중할지

전략화가 부족합니다.

---

## 권장

반드시 추가:

- ICP(이상 고객)
- 산업별 전략
- 중견기업 vs 대기업 전략
- 컨설팅사 연계 전략

---

# docs/regulatory.md 검토

## 총평

regulatory.md는 프로젝트의 가장 강한 문서 중 하나입니다.

특히:

- IFRS S1/S2
- KSSB
- 공시 체계
- 감사 대응
- Scope3

이 상당히 잘 연결되어 있습니다.

보통 개발 프로젝트에서는:

규제를 요약만 하는데,

현재 문서는:

# 시스템 요구사항으로 변환

하려는 시도가 보입니다.

이건 매우 좋습니다.

---

## 매우 좋은 부분

### 1. 규제 -> 시스템 요구사항 연결

예:

- auditability
- traceability
- reproducibility
- evidence retention

등으로 연결되는 부분.

이건 매우 중요합니다.

---

### 2. 데이터 보존/재현성 관점

매우 좋습니다.

ESG는:

"현재 값"

보다:

"당시 계산을 재현 가능해야 하는가"

가 중요합니다.

현재 철학은 맞는 방향입니다.

---

## 핵심 문제점

# 문제 1. 규제 변화 대응 전략 부족

현재 문서는:

현재 규제 설명은 좋지만,

# 변화 대응 전략

이 부족합니다.

ESG 규제는:

거의 확실하게 자주 바뀝니다.

---

## 반드시 필요

- Effective Date
- Regulation Versioning
- Mapping Version
- Deprecated Rule
- Transitional Policy

개념.

---

# 문제 2. 지역별 규제 차이 전략 부족

현재는:

KSSB/IFRS 중심인데,

실제 글로벌 확장 시:

- CSRD(EU)
- SEC Climate Rule
- 일본 SSBJ

등 차이가 매우 큽니다.

---

## 권장 방향

Regulation Engine 개념 도입.

예:

- disclosure framework
- taxonomy mapping
- jurisdiction
- applicability rule

---

# docs/spec.md 추가 검토

기존 검토보다 더 중요한 문제:

# 기술 스펙이 “플랫폼 운영 모델” 수준까지 내려오지 않았다

예:

- 배포 전략
- tenant provisioning
- migration strategy
- storage lifecycle
- reprocessing strategy
- disaster recovery

등.

실제 ESG 플랫폼은:

장기 보관 시스템이기 때문에:

운영 설계가 매우 중요합니다.

---

# docs/task.md 검토

현재 task 구조는:

- 기능 나열

성격이 강합니다.

하지만 실제 AI Agent 개발에서는:

# dependency-aware task system

이 필요합니다.

---

## 권장

Task는 반드시:

- prerequisite
- blocked by
- architectural dependency
- domain dependency
- regulatory dependency

를 가져야 합니다.

즉:

단순 TODO 리스트가 아니라:

# Directed Graph

형태로 관리하는 것이 좋습니다.

---

# 추가 최종 평가

현재 esg-t2 프로젝트는:

단순 ESG CRUD 프로젝트 수준을 상당히 넘어섰습니다.

특히:

- 규제 기반 사고
- immutability
- auditability
- AI-assisted development governance
- SDD 기반 개발 철학

은 상당히 강점입니다.

다만 이제 필요한 것은:

# “복잡성을 제어하는 메타 아키텍처”

입니다.

즉:

- 규칙 자체 관리
- 규제 버전 관리
- AI 행동 제어
- 운영 복잡성 관리
- 이벤트 복잡성 관리

를 위한 상위 거버넌스 계층이 필요합니다.

---

# 3. 문서별 상세 검토

---

# docs/prd.md 검토

## 총평

현재 PRD는:

- 전략 방향
- 규제 기준
- 핵심 기능
- 데이터 무결성
- 감사 대응

관점에서 상당히 우수합니다.

하지만:

- 제품 전략
- 사용자 운영 흐름
- 실제 현업 업무 프로세스
- 데이터 책임 구조
- 시스템 운영 조직

관점은 더 보완되어야 합니다.

---

# 3-1. 매우 좋은 부분

## 1) Scope 3를 MVP에 포함한 점

이건 매우 중요합니다.

실제 ESG 시장에서:

- Scope 1/2는 이미 기본 기능
- 진짜 어려운 건 Scope 3

입니다.

특히:

- Cat.1
- Cat.2
- Cat.11

선정은 현실적입니다.

좋은 이유:

- 실제 기업 수요와 맞음
- 공급망 이슈 반영
- 향후 확장 가능
- 차별화 가능

---

## 2) Verification Workspace(VW)

이 부분은 상당히 좋습니다.

많은 ESG 시스템이:

- 계산
- 입력
- 보고서

까지만 생각하고,

감사인 워크플로우를 놓칩니다.

하지만 실제 시장에서는:

"검증 대응"

이 핵심 pain point입니다.

따라서:

- Snapshot
- Evidence
- Read-only
- Audit Trail

구조는 매우 적절합니다.

---

## 3) 정책값 분리 원칙

이건 매우 중요합니다.

특히:

- 배출계수
- 규제 일정
- Formula
- Framework Mapping

을 하드코딩하지 않는 방향은 반드시 유지해야 합니다.

이 프로젝트의 장기 생존성을 높이는 핵심 설계입니다.

---

# 3-2. 핵심 문제점

# 문제 1. 사용자 역할 모델이 아직 단순함

현재 RBAC:

- SUPER_ADMIN
- TENANT_ADMIN
- ESG_MANAGER
- VERIFIER
- SUPPLIER

정도인데,

실제 ESG 프로젝트에서는:

- 사업장 담당자
- 법인 담당자
- 본사 ESG팀
- 내부 감사
- 외부 컨설턴트
- 회계법인
- 승인자
- 검토자

등 훨씬 세분화됩니다.

특히:

## 현재 가장 위험한 부분

"ESG_MANAGER가 너무 많은 권한을 가진다"

입니다.

이건:

- 권한 오남용
- 감사 문제
- 책임 추적 실패

를 유발할 수 있습니다.

---

## 개선 방향

RBAC만으로 가지 말고:

# ABAC(Attribute Based Access Control)

를 조기 도입하는 것이 좋습니다.

예:

- tenant_id
- entity_id
- department_id
- disclosure_year
- data_sensitivity
- approval_state

기반 정책.

즉:

"누가"

보다:

"어떤 조건에서 접근 가능한가"

중심으로 전환해야 합니다.

---

# 문제 2. 데이터 볼륨 추정이 없음

현재 문서는 기능은 많지만:

- 하루 데이터량
- 월간 업로드량
- Evidence 파일 크기
- AuditLog 증가량
- Snapshot 증가량

등 운영 관점 수치가 없습니다.

이건 매우 위험합니다.

왜냐하면 ESG 시스템은:

"파일 시스템"

이 되어버리기 쉽기 때문입니다.

특히:

- PDF
- Excel
- 증빙 이미지
- 공급망 데이터

가 폭증합니다.

---

## 반드시 추가해야 하는 것

### Capacity Planning 섹션

예:

| 항목 | 예상 |
|---|---|
| 테넌트 수 | 100 |
| 법인 수 | 3~100 |
| 연간 Evidence 파일 | 100만 |
| Snapshot 보관기간 | 10년 |
| AuditLog 증가량 | 월 1억 이벤트 |

등.

---

# 문제 3. 공급업체 포털은 생각보다 훨씬 어려움

현재 문서는 공급업체 포털을 비교적 간단하게 취급합니다.

하지만 실제로는:

- 공급업체 인증
- 공급업체 권한
- 공급업체별 데이터 포맷
- 다국어
- 응답률 관리
- 재요청
- 공급업체 온보딩
- 보안 문제

가 매우 큽니다.

실제 ESG 시장에서:

Scope 3 공급망 수집은 거의 별도 SaaS 수준입니다.

---

## 권장 방향

MVP에서는:

- 공급업체 직접 입력

보다:

- CSV 업로드
- 이메일 기반 요청
- 수동 승인

중심으로 단순화하는 것을 권장합니다.

현재 범위는 MVP 치고 너무 큽니다.

---

# 문제 4. 보고서 생성이 너무 단순하게 기술됨

현재:

- PDF 생성
- 보고서 생성

정도로 되어 있는데,

실제 ESG 공시는:

- Narrative
- Footnote
- Methodology
- Exception
- Assumption
- Boundary
- Forward-looking statement

등 텍스트 요소가 매우 중요합니다.

즉:

숫자 시스템이 아니라:

"문서 시스템"

입니다.

---

## 추가 권장

다음 개념 추가 필요:

- Report Section Template
- Narrative Versioning
- Approval Workflow
- Comment Thread
- Disclosure Freeze
- Draft/Review/Approved 상태

---

# 문제 5. ESG는 결국 “메타데이터 시스템”이다

현재 구조는:

- ActivityData
- EmissionRecord

중심인데,

실제 ESG 플랫폼 핵심은:

# Metadata

입니다.

예:

- 데이터 정의
- 측정 기준
- 적용 산식
- 데이터 품질
- 계산 근거
- 규제 매핑
- 출처

등.

즉:

"숫자"
보다
"숫자의 의미"
가 더 중요합니다.

---

## 반드시 강화해야 할 것

### Metadata Layer

독립 모듈화 권장.

예:

- MetricDefinition
- FormulaDefinition
- DisclosureMapping
- DataLineage
- EvidenceReference
- QualityRule

---

# docs/spec.md 검토

## 총평

기술 선택 자체는 현대적이고 방향성이 좋습니다.

특히:

- Spring Modulith
- OpenTelemetry
- PostgreSQL RLS
- Virtual Threads

선택은 꽤 적절합니다.

하지만:

기술 문서가 아직:

"스택 나열"
수준에 머물러 있습니다.

실제 중요한:

- 데이터 흐름
- 경계 전략
- 장애 전략
- 트랜잭션 전략
- 배치 전략
- 멀티테넌트 전략

이 부족합니다.

---

# 4-1. 가장 큰 기술적 위험

# Spring Modulith를 사용하는데 Module Boundary 정의가 약함

현재:

- ghg
- entity
- audit
- supply

등 모듈 이름은 존재하지만,

중요한:

- 공개 API
- 내부 API
- 이벤트 경계
- 트랜잭션 범위
- 모듈 소유 데이터

정의가 없습니다.

---

## 매우 중요한 개선 방향

각 모듈마다 반드시 정의 필요:

### 예시

| 항목 | 설명 |
|---|---|
| Owned Aggregate | 모듈이 소유하는 Aggregate |
| Published Events | 외부 공개 이벤트 |
| Internal Events | 내부 이벤트 |
| Allowed Dependencies | 허용 의존성 |
| External Contract | 외부 공개 API |
| Persistence Ownership | 테이블 소유권 |

---

# 문제 2. PostgreSQL 단일 DB 전략 위험

현재:

- 단일 PostgreSQL
- 멀티테넌트
- AuditLog
- Snapshot
- Evidence Metadata
- Scope3 대량 데이터

를 모두 처리하려고 합니다.

이건 초기에는 가능하지만:

성장 시 위험합니다.

---

## 특히 위험한 것

### AuditLog

AuditLog는:

- append only
- 매우 빠르게 증가
- 조회 패턴 특수
- 장기 보관

특성이 있습니다.

일반 업무 DB와 섞이면:

성능 문제가 발생하기 쉽습니다.

---

## 권장 방향

장기적으로:

- 업무 DB
- Audit/Event DB
- Object Metadata

분리 고려.

최소한:

- Partitioning
- Archive 전략
- Cold Storage

은 초기에 설계 필요.

---

# 문제 3. Event Architecture가 없음

현재:

Outbox Pattern은 언급되지만,

이벤트 체계 자체가 없습니다.

ESG 플랫폼은 결국:

# Event-driven System

이 됩니다.

예:

- 데이터 수정
- 재계산
- Snapshot 생성
- 검증 요청
- Approval
- 보고서 갱신

등이 연쇄적으로 발생.

---

## 권장

반드시 추가:

- Domain Event Catalog
- Event Naming Convention
- Retry Policy
- Idempotency Strategy
- Dead Letter Queue 정책

---

# 문제 4. 계산 엔진 설계 부족

현재:

GHG 계산 기능은 상세하지만,

실제 중요한:

- Formula Engine
- Calculation Graph
- Versioned Formula
- Recalculation Strategy
- Incremental Calculation

설계가 없습니다.

---

## 매우 중요

ESG 계산 시스템은:

CRUD 시스템이 아니라:

# Calculation Platform

입니다.

따라서:

- formula registry
- dependency graph
- recompute policy
- effective date

개념이 필요합니다.

---

# 문제 5. 배치 전략이 없음

Scope 3는:

실제로는 배치 시스템입니다.

현재 문서에서는:

Virtual Threads 정도만 언급되는데 부족합니다.

---

## 추가 필요

- Job Scheduler
- Batch Retry
- Chunk Processing
- Backpressure
- Long-running Job Tracking
- Progress Monitoring

---

# docs/plan.md 검토

현재 거의 비어 있습니다.

이건 프로젝트 전체에서 가장 큰 문제 중 하나입니다.

현재 PRD와 Spec은 상당히 거대한데:

실행 계획이 없습니다.

즉:

"좋은 전략 문서"

는 있지만:

"개발 가능한 계획"

이 없습니다.

---

# 반드시 추가해야 하는 것

# 1. 단계별 구현 전략

예:

## Phase 0
- 인증
- 멀티테넌트
- 기본 ActivityData

## Phase 1
- Scope1/2
- EmissionFactor
- Calculation Engine

## Phase 2
- Scope3
- 공급업체 포털

## Phase 3
- Verification Workspace

등.

---

# 2. 기술 리스크 우선 검증

현재 가장 위험한 것은:

- Scope3 계산
- Snapshot immutability
- RLS
- Audit Chain

입니다.

따라서:

초기 PoC 필요.

---

# 3. ADR 강화 필요

현재 ADR 구조는 존재하지만:

핵심 아키텍처 결정이 아직 부족합니다.

반드시 ADR로 남겨야 할 것:

- 왜 Modulith인가
- 왜 RLS인가
- 왜 INSERT-only인가
- 왜 PostgreSQL 단일 DB인가
- 왜 Event-driven인가
- 왜 Snapshot 기반인가

---

# 4. 문서 구조 자체에 대한 문제점

현재 문서들은:

- 전략
- 기능
- 기술

은 강하지만,

실제 개발 조직 운영 문서가 부족합니다.

---

# 반드시 추가 권장 문서

## 1. Architecture Overview

한 장으로:

- 시스템 흐름
- 모듈 관계
- 이벤트 흐름
- 데이터 흐름

보여주는 문서.

현재는 텍스트 중심이라 복잡성이 잘 안 보입니다.

---

## 2. Data Governance Guide

ESG에서는 매우 중요.

반드시 필요:

- 데이터 책임
- 데이터 승인
- 데이터 품질
- 변경 절차

---

## 3. Calculation Engine Spec

이 프로젝트 핵심.

별도 문서 필요.

현재 PRD에 섞여 있어서 부족합니다.

---

## 4. Security Architecture

현재 보안이 너무 얕게 적혀 있습니다.

반드시 필요:

- Tenant Isolation
- Object Storage Security
- Signed URL
- Key Rotation
- Secret Management
- Audit Access Policy

---

## 5. 운영 Runbook

실제 ESG 시스템은:

운영이 핵심입니다.

반드시:

- 장애 대응
- Snapshot 복구
- 배출계수 업데이트
- 재계산
- 감사 대응

절차 필요.

---

# 5. 가장 중요한 전략적 조언

# 이 프로젝트는 CRUD SaaS가 아니다

현재 문서를 보면:

조금씩:

"일반 업무 시스템"

처럼 흘러갈 위험이 있습니다.

하지만 실제로는:

# ESG Ledger + Calculation + Evidence Platform

에 가깝습니다.

즉 핵심은:

- 입력 화면
- REST API

가 아니라:

- 데이터 신뢰성
- 계산 재현성
- 규제 대응 가능성
- 감사 가능성
- Traceability

입니다.

이 철학을 끝까지 유지해야 합니다.

---

# 6. 우선순위 재조정 권장

현재 MVP 범위는 상당히 큽니다.

특히:

- Scope3
- Supplier Portal
- Verification Workspace
- Multi-Entity
- iXBRL 준비

를 동시에 하려는 것은 위험합니다.

---

# 추천 MVP 재구성

## 진짜 MVP

### 반드시 포함
- Multi-Entity
- Scope1/2
- Audit
- Snapshot
- Evidence
- Basic Report

### 축소 권장
- Scope3
- Supplier Portal
- Advanced Verification

---

# 7. 기술적으로 가장 중요한 영역

향후 가장 어려워질 가능성이 높은 영역:

1. Scope3 공급망 데이터 품질
2. Calculation Versioning
3. Re-disclosure/Recalculation
4. Snapshot Immutability
5. Multi-tenant Isolation
6. Evidence Traceability
7. Event Ordering
8. Long-term Audit Storage

입니다.

초기 설계부터:

- 단순 CRUD
- 즉흥 테이블 추가
- 서비스 간 직접 호출

형태로 가면 나중에 거의 반드시 재설계하게 됩니다.

---

# 8. 최종 평가

| 항목 | 평가 |
|---|---|
| ESG 도메인 이해 | 매우 우수 |
| 규제 이해 | 매우 우수 |
| 감사/무결성 관점 | 우수 |
| 제품 전략 | 양호 |
| 운영 전략 | 부족 |
| 아키텍처 상세도 | 중간 |
| 배치/이벤트 설계 | 부족 |
| 멀티테넌트 전략 | 중간 |
| 데이터 거버넌스 | 양호 |
| 실행 가능성 | 범위 조정 필요 |

---

# 9. 결론

현재 esg-t2는:

"ESG SaaS"

라기보다:

# “규제 대응형 ESG 데이터 플랫폼”

방향에 더 가깝습니다.

이 방향 자체는 매우 좋습니다.

특히:

- immutability
- auditability
- traceability
- reproducibility

철학은 매우 강점입니다.

다만 현재 가장 필요한 것은:

# “복잡성을 통제하는 능력”

입니다.

현재 문서는:

기능과 개념은 매우 풍부하지만,

실제 개발 가능한 형태로:

- 단계 분리
- 이벤트 구조화
- 데이터 책임 분리
- 운영 단순화

가 더 필요합니다.

잘 다듬으면 단순 ESG CRUD가 아니라:

상당히 강력한 ESG 공시/검증 플랫폼으로 발전 가능성이 있습니다.

---

## 참고 자료 및 검토 기준

- [KSSB 공식 사이트](https://www.kssb.kr?utm_source=chatgpt.com)
- [IFRS Sustainability Standards](https://www.ifrs.org/sustainability/?utm_source=chatgpt.com)
- [GHG Protocol](https://ghgprotocol.org?utm_source=chatgpt.com)
- ESG 공시 및 AI 기반 ESG 시스템 관련 최신 연구/사례 참고

검토 시 참고한 ESG/공시/아키텍처 자료:
- ESG Reporting Lifecycle Management with Large Language Models and AI Agents (2026)
- ESG 정보공시 관리 정보시스템 연구
- ESG Analysis Platform 사례

