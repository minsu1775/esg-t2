-- 연결 집계 배출량: Equity / Operational Control 방법론 산출 결과 (Phase 4)
-- INSERT-only: 재현성 원칙 (P1) — UPDATE/DELETE 금지 (migration-pg에서 REVOKE)
CREATE TABLE consolidated_emission_records (
    id                     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID            NOT NULL,
    root_entity_id         UUID            NOT NULL,
    reporting_year         INT             NOT NULL,
    scope                  VARCHAR(30)     NOT NULL,
    ghg_type               VARCHAR(30)     NOT NULL DEFAULT 'CO2E',
    consolidation_method   VARCHAR(30)     NOT NULL,
    total_emission         NUMERIC(20, 6)  NOT NULL,
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 법인별 기여분 상세 (개별 뷰 API용)
CREATE TABLE consolidated_emission_contributions (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    consolidated_record_id      UUID            NOT NULL REFERENCES consolidated_emission_records(id),
    entity_id                   UUID            NOT NULL,
    ownership_ratio             NUMERIC(5, 4),
    weighted_emission           NUMERIC(20, 6)  NOT NULL,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_consolidated_tenant_entity_year
    ON consolidated_emission_records (tenant_id, root_entity_id, reporting_year);
CREATE INDEX idx_consolidated_contributions_record
    ON consolidated_emission_contributions (consolidated_record_id);
