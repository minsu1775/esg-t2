-- activity_data: 활동 데이터 (배출량 산출의 원천 데이터, INSERT-only)
CREATE TABLE activity_data (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    entity_id       UUID         NOT NULL REFERENCES legal_entities(id),
    reporting_year  INT          NOT NULL,
    category        VARCHAR(50)  NOT NULL,
    sub_category    VARCHAR(100),
    quantity        NUMERIC(20, 6) NOT NULL,
    unit            VARCHAR(30)  NOT NULL,
    standard_value  NUMERIC(20, 6),
    standard_unit   VARCHAR(30),
    data_source     VARCHAR(50)  NOT NULL DEFAULT 'MANUAL',
    data_quality    VARCHAR(20)  NOT NULL DEFAULT 'AVERAGE_DATA',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    submitted_by    UUID,
    approved_by     UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_tenant_year ON activity_data (tenant_id, reporting_year);
CREATE INDEX idx_activity_entity_cat  ON activity_data (entity_id, category);

-- emission_records: 배출량 계산 결과 (INSERT-only, P1 재현성 보장)
CREATE TABLE emission_records (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    entity_id           UUID         NOT NULL REFERENCES legal_entities(id),
    activity_data_id    UUID         REFERENCES activity_data(id),
    reporting_year      INT          NOT NULL,
    scope               VARCHAR(20)  NOT NULL,
    ghg_type            VARCHAR(20)  NOT NULL DEFAULT 'CO2E',
    emission_factor_id  UUID         NOT NULL REFERENCES emission_factors(id),
    raw_emission        NUMERIC(20, 6) NOT NULL,
    is_consolidated     BOOLEAN      NOT NULL DEFAULT FALSE,
    calculated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_er_tenant_year  ON emission_records (tenant_id, reporting_year);
CREATE INDEX idx_er_entity_scope ON emission_records (entity_id, scope);
