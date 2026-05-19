-- audit_logs RLS + INSERT-only 권한 강제 (08-persistence.md)
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_logs
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

REVOKE UPDATE, DELETE ON audit_logs FROM app_user;
