-- ESG S/G 기본 지표 초기 데이터 (KSSB 2, prd.md 명세)
INSERT INTO esg_indicators (code, framework, category, sub_category, name_ko, unit, data_type, is_required, disclosure_ref) VALUES
    ('S-001', 'KSSB2', 'S', '인적자본', '여성 관리자 비율', '%', 'NUMERIC', TRUE, 'KSSB2-S10'),
    ('S-002', 'KSSB2', 'S', '안전보건', '산업재해율 (TRIR)', '건/백만인시', 'NUMERIC', TRUE, 'KSSB2-S20'),
    ('S-003', 'KSSB2', 'S', '인적자본', '자발적 이직률', '%', 'NUMERIC', FALSE, 'KSSB2-S11'),
    ('G-001', 'KSSB2', 'G', '이사회', '이사회 출석률', '%', 'NUMERIC', TRUE, 'KSSB2-G10'),
    ('G-002', 'KSSB2', 'G', '이사회', '독립사외이사 비율', '%', 'NUMERIC', TRUE, 'KSSB2-G11'),
    ('E-001', 'KSSB2', 'E', '기후변화', 'Scope 1 직접 배출량', 'tCO2e', 'NUMERIC', TRUE, 'KSSB2-E10'),
    ('E-002', 'KSSB2', 'E', '기후변화', 'Scope 2 간접 배출량 (location-based)', 'tCO2e', 'NUMERIC', TRUE, 'KSSB2-E11'),
    ('E-003', 'KSSB2', 'E', '기후변화', 'Scope 2 간접 배출량 (market-based)', 'tCO2e', 'NUMERIC', FALSE, 'KSSB2-E12')
ON CONFLICT (code) DO NOTHING;
