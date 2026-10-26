CREATE TABLE coding_problems (
    id           UUID PRIMARY KEY,
    topic_id     UUID         NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    position     SMALLINT     NOT NULL CHECK (position BETWEEN 1 AND 5),
    difficulty   VARCHAR(10)  NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    title        VARCHAR(120) NOT NULL,
    statement    TEXT         NOT NULL,
    starter_code TEXT         NOT NULL,
    UNIQUE (topic_id, position)
);

-- Programs read stdin and write stdout. Hidden tests are never sent to the browser.
CREATE TABLE coding_tests (
    id         UUID PRIMARY KEY,
    problem_id UUID     NOT NULL REFERENCES coding_problems (id) ON DELETE CASCADE,
    position   INT      NOT NULL,
    input      TEXT     NOT NULL,
    expected   TEXT     NOT NULL,
    visible    BOOLEAN  NOT NULL,
    UNIQUE (problem_id, position)
);

CREATE TABLE coding_submissions (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id   UUID        NOT NULL REFERENCES coding_problems (id) ON DELETE CASCADE,
    mode         VARCHAR(6)  NOT NULL CHECK (mode IN ('RUN', 'SUBMIT')),
    code         TEXT        NOT NULL,
    status       VARCHAR(20) NOT NULL,
    passed_tests INT         NOT NULL,
    total_tests  INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_coding_submissions_user_time ON coding_submissions (user_id, created_at DESC);
CREATE INDEX idx_coding_submissions_user_problem ON coding_submissions (user_id, problem_id, created_at DESC);

-- A problem counts as solved once a SUBMIT passes every test, hidden ones included.
CREATE TABLE coding_solved (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    problem_id UUID        NOT NULL REFERENCES coding_problems (id) ON DELETE CASCADE,
    solved_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, problem_id)
);
