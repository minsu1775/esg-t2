-- 배출계수 UNIQUE 제약 교체: effective_from 포함으로 버전 이력 보존 가능 (P1 재현성)
-- 기존 (source, category, sub_category, country_code, reporting_year) 제약 삭제 후
-- (source, category, sub_category, country_code, reporting_year, effective_from) 제약 추가
-- → 동일 키에 서로 다른 effective_from 값의 레코드 공존 허용 (배출계수 버전 관리)
-- 주의: DO 블록은 PostgreSQL 전용 문법. H2 로컬 보조 환경에서는 이 마이그레이션 미적용.

DO $$
DECLARE
    v_constraint TEXT;
BEGIN
    SELECT conname
    INTO v_constraint
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'emission_factors'
      AND c.contype = 'u'
    LIMIT 1;

    IF FOUND THEN
        EXECUTE 'ALTER TABLE emission_factors DROP CONSTRAINT ' || quote_ident(v_constraint);
    END IF;
END $$;

ALTER TABLE emission_factors
    ADD CONSTRAINT uq_ef_source_key_effective
    UNIQUE (source, category, sub_category, country_code, reporting_year, effective_from);
