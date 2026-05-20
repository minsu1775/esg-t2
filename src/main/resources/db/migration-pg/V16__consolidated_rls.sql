-- 연결 집계 테이블 RLS + INSERT-only 권한 (P0+P1)
ALTER TABLE consolidated_emission_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON consolidated_emission_records
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
REVOKE UPDATE, DELETE ON consolidated_emission_records FROM app_user;

ALTER TABLE consolidated_emission_contributions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON consolidated_emission_contributions
    FOR ALL TO app_user
    USING (consolidated_record_id IN (
        SELECT id FROM consolidated_emission_records
        WHERE tenant_id = current_setting('app.current_tenant_id')::UUID
    ));
REVOKE UPDATE, DELETE ON consolidated_emission_contributions FROM app_user;
