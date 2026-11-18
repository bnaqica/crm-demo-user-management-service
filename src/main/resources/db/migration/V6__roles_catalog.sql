-- Permissions are registered by the services that enforce them; roles bundle them. Both live in Keycloak as client
-- roles (crm-api for INTERNAL, portal-app for EXTERNAL); these tables hold what Keycloak can't: owner, flags, history.
CREATE TABLE permission (
    id             UUID PRIMARY KEY,
    permission_key VARCHAR(255)  NOT NULL,     -- <resource>:read, <resource>:write or ui:<feature>
    service        VARCHAR(255)  NOT NULL,     -- the service that registered it
    realm          VARCHAR(16)   NOT NULL,     -- INTERNAL (crm-api) or EXTERNAL (portal-app)
    description    VARCHAR(1000) NOT NULL,
    sensitive      BOOLEAN       NOT NULL DEFAULT FALSE,   -- never added to Super Manager automatically
    created_at     TIMESTAMPTZ   NOT NULL,
    deprecated_at  TIMESTAMPTZ                             -- no longer registered; not grantable
);

CREATE UNIQUE INDEX ux_permission_key ON permission (realm, permission_key);
CREATE INDEX idx_permission_service ON permission (service);

CREATE TABLE role (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,      -- also the Keycloak role name
    description   VARCHAR(1000) NOT NULL,
    owner         VARCHAR(255)  NOT NULL,
    assignable_to VARCHAR(16)   NOT NULL,      -- INTERNAL or EXTERNAL
    built_in      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_role_name ON role (lower(name));

CREATE TABLE role_permission (
    role_id       UUID NOT NULL REFERENCES role (id),
    permission_id UUID NOT NULL REFERENCES permission (id),
    PRIMARY KEY (role_id, permission_id)
);

-- A privileged change (Super Manager, a new admin) waiting for a second admin
CREATE TABLE grant_request (
    id           UUID PRIMARY KEY,
    type         VARCHAR(32)  NOT NULL,        -- SUPER_MANAGER or ADMIN_ACCOUNT
    target       VARCHAR(320) NOT NULL,        -- user sub, or the new admin's email
    payload      JSONB        NOT NULL,
    requested_by VARCHAR(255) NOT NULL,        -- admin sub
    status       VARCHAR(16)  NOT NULL,        -- PENDING, APPROVED, REJECTED, EXPIRED
    ticket_ref   VARCHAR(64)  NOT NULL,
    decided_by   VARCHAR(255),
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_grant_request_status ON grant_request (status, created_at);

INSERT INTO role (id, name, description, owner, assignable_to, built_in, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'super-manager',
     'Every non-sensitive CRM permission, across all teams', 'ums', 'INTERNAL', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000002', 'team-manager', 'Manages a team''s CRM data', 'ums', 'INTERNAL', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'team-member', 'Works with a team''s CRM data', 'ums', 'INTERNAL', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000004', 'external-starter', 'Self-registered portal user', 'ums', 'EXTERNAL', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000005', 'external-standard', 'Promoted or invited portal user', 'ums', 'EXTERNAL', TRUE, now(), now());
