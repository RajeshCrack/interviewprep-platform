CREATE TABLE users (
    id            UUID PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE goals (
    user_id       UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    hours_per_day NUMERIC(3, 1) NOT NULL CHECK (hours_per_day BETWEEN 0.5 AND 4),
    reminder_time VARCHAR(5)    NOT NULL,
    channel       VARCHAR(10)   NOT NULL CHECK (channel IN ('PUSH', 'EMAIL'))
);

CREATE TABLE user_stats (
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    streak         INT  NOT NULL DEFAULT 0,
    last_goal_date DATE,
    xp             INT  NOT NULL DEFAULT 0
);

CREATE TABLE study_logs (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    minutes INT  NOT NULL CHECK (minutes >= 0),
    PRIMARY KEY (user_id, day)
);
