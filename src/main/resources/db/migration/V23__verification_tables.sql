-- ============================================================
-- V23: 외부 검증 워크스페이스 테이블 (공통 DDL)
-- verification_snapshots, verification_comments, verification_signatures
-- ============================================================

-- 검증 스냅샷: APPROVED 보고서의 SHA-256 불변 복사본
CREATE TABLE verification_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL REFERENCES disclosure_reports(id),
    snapshot_hash   VARCHAR(64) NOT NULL,  -- SHA-256 hex (64자)
    snapshot_data   JSONB NOT NULL,        -- 보고서 내용 불변 복사
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    frozen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_snapshots_tenant ON verification_snapshots(tenant_id);
CREATE INDEX idx_verification_snapshots_report ON verification_snapshots(report_id);

-- 검증 코멘트: VERIFIER가 작성하는 의견 (append-only)
CREATE TABLE verification_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES verification_snapshots(id),
    tenant_id   UUID NOT NULL,
    author_id   UUID NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_comments_snapshot ON verification_comments(snapshot_id);

-- 검증 서명: VERIFIER의 최종 서명 (스냅샷당 1건 UNIQUE)
-- 스냅샷 본체를 불변으로 유지하기 위해 별도 테이블로 분리
CREATE TABLE verification_signatures (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL UNIQUE REFERENCES verification_snapshots(id),
    tenant_id   UUID NOT NULL,
    signed_by   UUID NOT NULL,
    signed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sign_note   TEXT
);

CREATE INDEX idx_verification_signatures_snapshot ON verification_signatures(snapshot_id);
