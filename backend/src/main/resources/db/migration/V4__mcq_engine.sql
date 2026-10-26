CREATE TABLE mcq_questions (
    id             UUID PRIMARY KEY,
    topic_id       UUID        NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    kind           VARCHAR(10) NOT NULL CHECK (kind IN ('THEORY', 'CODE')),
    difficulty     VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'HARD')),
    prompt         TEXT        NOT NULL,
    code_snippet   TEXT,
    option_1       TEXT        NOT NULL,
    option_2       TEXT        NOT NULL,
    option_3       TEXT        NOT NULL,
    option_4       TEXT        NOT NULL,
    correct_option SMALLINT    NOT NULL CHECK (correct_option BETWEEN 0 AND 3),
    explanation    TEXT        NOT NULL
);
CREATE INDEX idx_mcq_questions_cell ON mcq_questions (topic_id, kind, difficulty);

CREATE TABLE mcq_attempts (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic_id     UUID        NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'SUBMITTED', 'EXPIRED')),
    started_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    score        INT,
    passed       BOOLEAN
);
CREATE INDEX idx_mcq_attempts_user_topic ON mcq_attempts (user_id, topic_id, started_at DESC);

-- option_order maps what the student saw to the stored options: displayed option k shows stored option order[k].
CREATE TABLE mcq_attempt_questions (
    attempt_id       UUID     NOT NULL REFERENCES mcq_attempts (id) ON DELETE CASCADE,
    position         SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 20),
    question_id      UUID     NOT NULL REFERENCES mcq_questions (id),
    option_order     VARCHAR(7) NOT NULL,
    selected_display SMALLINT CHECK (selected_display BETWEEN 0 AND 3),
    PRIMARY KEY (attempt_id, position)
);

-- A student has at most one running test per topic.
CREATE UNIQUE INDEX one_active_attempt ON mcq_attempts (user_id, topic_id) WHERE status = 'ACTIVE';
