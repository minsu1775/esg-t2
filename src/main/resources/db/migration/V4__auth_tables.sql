CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ,
    UNIQUE(tenant_id, email)
);

CREATE TABLE user_roles (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id   UUID        NOT NULL REFERENCES users(id),
    role      VARCHAR(50) NOT NULL,  -- TENANT_ADMIN, ESG_MANAGER, ESG_VIEWER, VERIFIER, SUPPLIER, SUPER_ADMIN
    entity_id UUID        REFERENCES legal_entities(id),  -- null = 테넌트 전체 범위
    UNIQUE(user_id, role, entity_id)
);
