CREATE TABLE courses (
    id          UUID PRIMARY KEY,
    slug        VARCHAR(60)  NOT NULL UNIQUE,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(300) NOT NULL,
    icon        VARCHAR(4)   NOT NULL,
    published   BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INT          NOT NULL
);

CREATE TABLE topics (
    id            UUID PRIMARY KEY,
    course_id     UUID         NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    position      INT          NOT NULL CHECK (position >= 1),
    title         VARCHAR(120) NOT NULL,
    est_minutes   INT          NOT NULL CHECK (est_minutes > 0),
    what_text     TEXT,
    why_text      TEXT,
    example_code  TEXT,
    hints         TEXT,
    video_query   VARCHAR(200),
    has_content   BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (course_id, position)
);

-- One row per student and topic. Rows are created only by the gate engine (phase 3 and 4),
-- never from a client-supplied topic status.
CREATE TABLE topic_progress (
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic_id         UUID        NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    mcq_passed_at    TIMESTAMPTZ,
    coding_passed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, topic_id)
);
