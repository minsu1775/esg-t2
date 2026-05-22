-- supplier_invitations: 공급업체 초대 (T-6-07)
CREATE TABLE supplier_invitations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    entity_id   UUID         NOT NULL REFERENCES legal_entities(id),
    email       VARCHAR(320) NOT NULL,
    token       UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    invited_by  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT supplier_invitations_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED'))
);

CREATE INDEX idx_supply_inv_tenant ON supplier_invitations (tenant_id);
CREATE INDEX idx_supply_inv_token  ON supplier_invitations (token);
