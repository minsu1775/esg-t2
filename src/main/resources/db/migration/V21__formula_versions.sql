-- V21__formula_versions.sql
-- Formula DSL 버전 관리 테이블
CREATE TABLE formula_versions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50) NOT NULL,
    version      VARCHAR(20) NOT NULL,
    expression   TEXT        NOT NULL,
    ghg_category VARCHAR(50),
    yaml_content TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    activated_by UUID,
    created_at   TIMESTAMPTZ NOT NULL  DEFAULT NOW(),
    UNIQUE (code, version),
    CONSTRAINT formula_versions_status_check
        CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE INDEX idx_formula_versions_code_status ON formula_versions(code, status);
