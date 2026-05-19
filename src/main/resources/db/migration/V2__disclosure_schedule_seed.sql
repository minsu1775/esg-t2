-- 데모 테넌트 (로컬 개발 및 테스트용)
-- id는 앱 계층(JPA)에서 생성하는 것이 원칙이나, seed 데이터는 고정 UUID 사용
INSERT INTO tenants (id, code, name, country_code)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEMO', '데모 법인', 'KR')
ON CONFLICT (code) DO NOTHING;

-- 글로벌 공시 의무 일정 (규제: 2026-02-26 KSSB 1/2 고시 기준, CSRD 2023/2024 기준)
-- disclosure_schedules 는 전체 테넌트 공통 참조 테이블 (tenant_id 없음)
INSERT INTO disclosure_schedules (id, jurisdiction, framework, entity_criteria, mandatory_from_year, scope3_from_year, notes, source_document)
VALUES
  -- KSSB 1 (한국판 IFRS S1 — 지속가능성 관련 재무정보 공시 일반 요건)
  ('10000000-0000-0000-0000-000000000001', 'KR', 'KSSB1', '자산총액 2조 원 이상 상장사', 2026, NULL,
   '1단계: 2026회계연도부터 대형 상장사 의무 적용', 'KSSB 1호 (2026-02-26)'),

  -- KSSB 2 (한국판 IFRS S2 — 기후 관련 공시)
  ('10000000-0000-0000-0000-000000000002', 'KR', 'KSSB2', '자산총액 2조 원 이상 상장사', 2026, 2030,
   '1단계: 2026회계연도부터 Scope 1·2 의무. Scope 3는 2030년부터 단계적 적용', 'KSSB 2호 (2026-02-26)'),

  ('10000000-0000-0000-0000-000000000003', 'KR', 'KSSB1', '자산총액 5000억 원 이상 상장사', 2028, NULL,
   '2단계: 2028회계연도로 확대 적용', 'KSSB 1호 (2026-02-26)'),

  ('10000000-0000-0000-0000-000000000004', 'KR', 'KSSB2', '자산총액 5000억 원 이상 상장사', 2028, 2031,
   '2단계: 2028회계연도로 확대 적용', 'KSSB 2호 (2026-02-26)'),

  -- ISSB S2 (글로벌 기후 공시)
  ('20000000-0000-0000-0000-000000000001', 'GLOBAL', 'ISSB_S2', NULL, 2024, 2026,
   'IFRS S2: Scope 1·2 즉시 적용, Scope 3는 1년 유예 후 2026년부터', 'IFRS S2 (2023-06)'),

  -- CSRD (EU 기업 지속가능성 보고 지침)
  ('30000000-0000-0000-0000-000000000001', 'EU', 'CSRD', '대형 공익법인 (직원 500명 이상)', 2024, 2025,
   'ESRS 기준 — 2024회계연도부터 대형 공익법인 의무', 'CSRD Directive 2022/2464/EU'),

  ('30000000-0000-0000-0000-000000000002', 'EU', 'CSRD', '기타 대형 기업 (직원 250명 이상 또는 매출 4000만 유로 이상)', 2025, 2026,
   '2025회계연도부터 기타 대형 기업 의무', 'CSRD Directive 2022/2464/EU')

ON CONFLICT (jurisdiction, framework, mandatory_from_year) DO NOTHING;
