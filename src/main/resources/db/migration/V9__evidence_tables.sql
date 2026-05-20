-- evidence_files: 증빙 파일 메타데이터 (SHA-256 무결성, 경로 순회 방어, 10-evidence-files.md)
CREATE TABLE evidence_files (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    stored_filename   VARCHAR(500) NOT NULL,
    storage_uri       VARCHAR(2000) NOT NULL,
    mime_type         VARCHAR(200),
    file_size_bytes   BIGINT,
    sha256_hash       CHAR(64)     NOT NULL,
    uploaded_by       UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evidence_tenant ON evidence_files (tenant_id);

-- activity_data_evidence: 활동 데이터 ↔ 증빙 파일 N:M 연결
CREATE TABLE activity_data_evidence (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_data_id UUID        NOT NULL REFERENCES activity_data(id),
    evidence_file_id UUID        NOT NULL REFERENCES evidence_files(id),
    linked_by        UUID        NOT NULL,
    linked_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (activity_data_id, evidence_file_id)
);

CREATE INDEX idx_ade_activity ON activity_data_evidence (activity_data_id);
