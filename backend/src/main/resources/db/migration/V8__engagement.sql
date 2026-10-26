-- In-app notifications: reminders and badge awards.
CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(20)  NOT NULL,
    title      VARCHAR(120) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- At most one reminder per student per day, even if two servers run the scheduler.
CREATE TABLE reminder_log (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE user_badges (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge     VARCHAR(30) NOT NULL,
    earned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, badge)
);
