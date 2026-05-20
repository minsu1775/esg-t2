-- activity_data 테이블 RLS (P0: 테넌트 격리, 03-security.md)
ALTER TABLE activity_data ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON activity_data
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);
