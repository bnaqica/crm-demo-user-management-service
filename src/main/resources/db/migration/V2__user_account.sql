-- One row per internal or external user the UMS manages. Keycloak is the identity source; this table holds the
-- UMS-owned status, a copy of the fields used for search, and the cross-realm email uniqueness.
CREATE TABLE user_account (
    sub            UUID PRIMARY KEY,           -- Keycloak user id, the token's sub
    user_type      VARCHAR(16)  NOT NULL,      -- INTERNAL (crm-internal) or EXTERNAL (portal)
    email          VARCHAR(320) NOT NULL,
    first_name     VARCHAR(255),
    last_name      VARCHAR(255),
    status         VARCHAR(16)  NOT NULL,      -- INVITED, STARTER, ACTIVE, DISABLED
    created_ticket VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);

-- Email is the business key across both realms, compared without case
CREATE UNIQUE INDEX ux_user_account_email ON user_account (lower(email));
CREATE INDEX idx_user_account_status ON user_account (status);
