-- activity_data: Cat.11 사용기간 컬럼 추가 (nullable, Cat.11 전용)
ALTER TABLE activity_data ADD COLUMN IF NOT EXISTS lifetime_years INT;

-- emission_records: Scope 3 카테고리 번호 컬럼 추가 (1~16, Category 16 스키마 준비)
ALTER TABLE emission_records ADD COLUMN IF NOT EXISTS scope3_category INT;
ALTER TABLE emission_records
    ADD CONSTRAINT emission_records_scope3_category_check
    CHECK (scope3_category IS NULL OR (scope3_category >= 1 AND scope3_category <= 16));

-- scope3_coverage_reports: 커버리지 보고서 (append-only, P1 재현성)
CREATE TABLE scope3_coverage_reports (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    entity_id             UUID NOT NULL REFERENCES legal_entities(id),
    reporting_year        INT NOT NULL,
    included_categories   TEXT NOT NULL,   -- JSON 배열: "[1,2,11]"
    excluded_categories   TEXT,            -- JSON 배열: "[4,6]"
    exclusion_reasons     TEXT,            -- JSON 객체: {"4":"사유","6":"사유"}
    coverage_pct          NUMERIC(5, 2) NOT NULL,
    meets_95pct_threshold BOOLEAN NOT NULL,
    generated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scope3_coverage_tenant_entity
    ON scope3_coverage_reports(tenant_id, entity_id, reporting_year);
