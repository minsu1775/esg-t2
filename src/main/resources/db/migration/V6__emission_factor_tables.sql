-- emission_factors: 배출계수 마스터 (KEEI, DEFRA, IPCC_AR6)
-- effective_from/to: resolveAt() 과거 재현성 보장 (06-emission-calculation.md)
CREATE TABLE emission_factors (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source          VARCHAR(50)  NOT NULL,
    category        VARCHAR(100) NOT NULL,
    sub_category    VARCHAR(100),
    country_code    CHAR(2),
    reporting_year  INT          NOT NULL,
    gwp_source      VARCHAR(30)  NOT NULL DEFAULT 'IPCC_AR6',
    factor_value    NUMERIC(20, 8) NOT NULL,
    unit            VARCHAR(50)  NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source, category, sub_category, country_code, reporting_year)
);

CREATE INDEX idx_ef_category_date ON emission_factors (category, effective_from, effective_to);
CREATE INDEX idx_ef_source_year ON emission_factors (source, reporting_year);
