CREATE TABLE legal_entities (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    name         VARCHAR(200) NOT NULL,
    country_code CHAR(2)      NOT NULL,
    entity_type  VARCHAR(30)  NOT NULL,  -- PARENT, SUBSIDIARY, ASSOCIATE
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ,
    UNIQUE(tenant_id, name)
);

CREATE TABLE entity_relationships (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    parent_id       UUID         NOT NULL REFERENCES legal_entities(id),
    child_id        UUID         NOT NULL REFERENCES legal_entities(id),
    ownership_ratio NUMERIC(5,4) NOT NULL,
    method          VARCHAR(50)  NOT NULL DEFAULT 'EQUITY',  -- EQUITY, OPERATIONAL_CONTROL
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (ownership_ratio > 0 AND ownership_ratio <= 1),
    CHECK (parent_id != child_id)
);
