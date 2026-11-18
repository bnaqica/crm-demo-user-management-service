-- Teams of internal users and their members. CRM services read a user's teams through the team-scope API.
CREATE TABLE team (
    id             UUID PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    created_ticket VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    archived_at    TIMESTAMPTZ,              -- archived teams keep their history but take no new members
    version        BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_team_name ON team (lower(name));

CREATE TABLE team_member (
    team_id  UUID    NOT NULL REFERENCES team (id),
    user_sub UUID    NOT NULL REFERENCES user_account (sub),
    manager  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (team_id, user_sub)
);

CREATE INDEX idx_team_member_user ON team_member (user_sub);

-- An internal user may be in more than one team only when an admin allows it
ALTER TABLE user_account ADD COLUMN multi_team_allowed BOOLEAN NOT NULL DEFAULT FALSE;
