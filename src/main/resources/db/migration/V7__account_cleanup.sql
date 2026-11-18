-- Why an account is in its status, when the UMS set it by itself (such as STARTER_EXPIRED); null otherwise.
ALTER TABLE user_account ADD COLUMN status_reason VARCHAR(64);

CREATE INDEX idx_user_account_status_created ON user_account (status, created_at);

-- ShedLock: one instance at a time runs each scheduled job
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
