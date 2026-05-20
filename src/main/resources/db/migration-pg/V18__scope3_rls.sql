-- scope3_coverage_reports RLS (P0: 테넌트 격리)
ALTER TABLE scope3_coverage_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON scope3_coverage_reports
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

-- Append-only 강제: DELETE/UPDATE 권한 박탈 (08-persistence.md)
REVOKE UPDATE, DELETE ON scope3_coverage_reports FROM app_user;
