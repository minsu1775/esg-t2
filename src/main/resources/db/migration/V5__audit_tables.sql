-- audit_logs: INSERT-only, Hash Chain 무결성
CREATE TABLE audit_logs (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    actor_id      UUID         NOT NULL,
    entity_id     UUID,
    entity_type   VARCHAR(100),
    previous_hash VARCHAR(64),
    current_hash  VARCHAR(64)  NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_logs_tenant ON audit_logs (tenant_id, id DESC);

-- outbox_events: Outbox Pattern — 트랜잭션 내 저장, 폴러가 처리
CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    actor_id     UUID         NOT NULL,
    entity_id    UUID,
    entity_type  VARCHAR(100),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox_events (status, created_at);
