-- emission_records RLS + INSERT-only 권한 강제 (08-persistence.md, P1 재현성 보장)
ALTER TABLE emission_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON emission_records
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

REVOKE UPDATE, DELETE ON emission_records FROM app_user;
