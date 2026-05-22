-- ============================================================
-- V23 (PostgreSQL 전용): vw 모듈 RLS + 스냅샷 불변성 트리거
-- ============================================================

-- ============================================================
-- 스냅샷 불변성: BEFORE UPDATE/DELETE 트리거
-- ⚠️ CREATE RULE DO INSTEAD NOTHING 방식은 PG 레거시로 오류 없이
--    조용히 무시됨 → 반드시 RAISE EXCEPTION 트리거 방식 사용
-- ============================================================
CREATE OR REPLACE FUNCTION prevent_snapshot_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'verification_snapshots는 불변입니다. UPDATE/DELETE 금지.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER snapshot_immutable
    BEFORE UPDATE OR DELETE ON verification_snapshots
    FOR EACH ROW EXECUTE FUNCTION prevent_snapshot_modification();

-- DB 권한 박탈: app_user가 직접 UPDATE/DELETE 불가 (08-persistence.md)
REVOKE UPDATE, DELETE ON verification_snapshots FROM app_user;

-- ============================================================
-- verification_snapshots RLS
-- ============================================================
ALTER TABLE verification_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_snapshots FORCE ROW LEVEL SECURITY;

-- 기본 테넌트 격리
CREATE POLICY tenant_isolation_snapshots ON verification_snapshots
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);

-- VERIFIER 추가 격리: 지정 snapshot_id만 조회 허용
-- app.verifier_snapshot_id 미설정(NULL) → 테넌트 격리만 적용 (ESG_MANAGER 등)
-- app.verifier_snapshot_id 설정 → 해당 스냅샷만 접근 허용
CREATE POLICY verifier_snapshot_isolation ON verification_snapshots
    FOR SELECT TO app_user
    USING (
        current_setting('app.verifier_snapshot_id', true) IS NULL
        OR id = current_setting('app.verifier_snapshot_id', true)::UUID
    );

-- ============================================================
-- verification_comments RLS
-- ============================================================
ALTER TABLE verification_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_comments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_comments ON verification_comments
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);

-- ============================================================
-- verification_signatures RLS
-- ============================================================
ALTER TABLE verification_signatures ENABLE ROW LEVEL SECURITY;
ALTER TABLE verification_signatures FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_signatures ON verification_signatures
    FOR ALL TO app_user
    USING (tenant_id = current_setting('app.current_tenant_id', true)::UUID);
