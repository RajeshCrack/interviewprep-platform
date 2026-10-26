package com.gateready.engage;

import com.gateready.engage.BadgeRules.Badge;
import com.gateready.engage.BadgeRules.Snapshot;
import com.gateready.goal.StreakCalculator;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BadgeService {
    public record BadgeView(String key, String title, String description, boolean earned, Instant earnedAt) {}

    private final JdbcTemplate jdbc;
    private final NotificationDao notifications;
    private final Clock clock;

    public BadgeService(JdbcTemplate jdbc, NotificationDao notifications, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Awards every badge the student now qualifies for and tells them about the new ones. Safe to call any time. */
    public List<Badge> award(UUID userId) {
        Instant now = clock.instant();
        List<Badge> fresh = new ArrayList<>();
        for (Badge b : BadgeRules.earned(snapshot(userId))) {
            int inserted = jdbc.update("INSERT INTO user_badges (user_id, badge, earned_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    userId, b.name(), Timestamp.from(now));
            if (inserted == 1) {
                fresh.add(b);
                notifications.add(userId, "BADGE", "Badge earned: " + b.title, b.description, now);
            }
        }
        return fresh;
    }

    public List<BadgeView> badges(UUID userId) {
        Map<String, Instant> earned = jdbc.query("SELECT badge, earned_at FROM user_badges WHERE user_id = ?",
                (rs, i) -> Map.entry(rs.getString(1), rs.getTimestamp(2).toInstant()), userId)
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<BadgeView> out = new ArrayList<>();
        for (Badge b : Badge.values()) {
            out.add(new BadgeView(b.name(), b.title, b.description, earned.containsKey(b.name()), earned.get(b.name())));
        }
        return out;
    }

    private Snapshot snapshot(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        Integer streak = jdbc.query("SELECT streak, last_goal_date FROM user_stats WHERE user_id = ?",
                rs -> rs.next() ? StreakCalculator.current(rs.getObject(2, LocalDate.class), today, rs.getInt(1)) : 0, userId);
        Integer mcq = jdbc.queryForObject("SELECT count(*) FROM topic_progress WHERE user_id = ? AND mcq_passed_at IS NOT NULL", Integer.class, userId);
        Integer cleared = jdbc.queryForObject("SELECT count(*) FROM topic_progress WHERE user_id = ? AND mcq_passed_at IS NOT NULL AND coding_passed_at IS NOT NULL", Integer.class, userId);
        Integer solved = jdbc.queryForObject("SELECT count(*) FROM coding_solved WHERE user_id = ?", Integer.class, userId);
        return new Snapshot(streak == null ? 0 : streak, mcq == null ? 0 : mcq, solved == null ? 0 : solved, cleared == null ? 0 : cleared);
    }

    /** Used by tests and the controller to show which keys exist. */
    static Set<String> allKeys() {
        return java.util.Arrays.stream(Badge.values()).map(Enum::name).collect(Collectors.toSet());
    }
}
