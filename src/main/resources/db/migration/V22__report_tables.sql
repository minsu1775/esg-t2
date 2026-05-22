-- V22__report_tables.sql
-- 공시 보고서 테이블 생성 (Phase 7: rpt 모듈)
CREATE TABLE disclosure_reports (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    entity_id       UUID        NOT NULL REFERENCES legal_entities(id),
    reporting_year  INT         NOT NULL,
    framework       VARCHAR(30) NOT NULL DEFAULT 'KSSB2',
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content         JSONB       NOT NULL DEFAULT '{}',
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at    TIMESTAMPTZ,
    approved_at     TIMESTAMPTZ,
    approved_by     UUID,
    rejection_reason TEXT,
    CONSTRAINT disclosure_reports_status_check
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED'))
);

CREATE INDEX idx_disclosure_reports_tenant_year
    ON disclosure_reports(tenant_id, reporting_year);
CREATE INDEX idx_disclosure_reports_entity_year
    ON disclosure_reports(entity_id, reporting_year);
