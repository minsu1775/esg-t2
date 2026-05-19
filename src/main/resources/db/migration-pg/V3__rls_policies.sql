-- PostgreSQL RLS 정책 — legal_entities, entity_relationships
-- TenantContextInterceptor가 매 요청마다 SET LOCAL app.current_tenant_id 를 실행해야 동작함

ALTER TABLE legal_entities ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON legal_entities
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE entity_relationships ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON entity_relationships
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id')::UUID);

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON user_roles
    FOR ALL TO app_user
    USING (user_id IN (
        SELECT id FROM users WHERE tenant_id = current_setting('app.current_tenant_id')::UUID
    ));
