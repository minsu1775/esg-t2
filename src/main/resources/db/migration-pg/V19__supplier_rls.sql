-- RLS: supplier_invitations 테넌트 격리
ALTER TABLE supplier_invitations ENABLE ROW LEVEL SECURITY;

CREATE POLICY supplier_invitations_tenant_isolation ON supplier_invitations
    FOR ALL TO PUBLIC
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));
