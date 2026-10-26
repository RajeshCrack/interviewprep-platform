package com.gateready.engage;

import com.gateready.goal.StreakCalculator;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends each student at most one reminder a day, at the time they chose, if they have not met their goal.
 * Every reminder becomes an in-app notification; students who chose email also get an email.
 */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final NotificationDao notifications;
    private final EmailSender email;
    private final Clock clock;

    public ReminderService(JdbcTemplate jdbc, NotificationDao notifications, EmailSender email, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.email = email;
        this.clock = clock;
    }

    private record Candidate(UUID id, String name, String email, double hours, String reminderTime, String channel,
                             int streak, LocalDate lastGoalDate, int minutesToday, boolean emailVerified) {}

    /** Sends every reminder that is due right now. Returns how many were sent. */
    public int sendDueReminders() {
        Instant instant = clock.instant();
        ZonedDateTime local = instant.atZone(clock.getZone());
        LocalDate today = local.toLocalDate();
        LocalTime now = local.toLocalTime();
        String nowText = now.format(HHMM);
        String earliest = now.minusMinutes(ReminderRules.WINDOW_MINUTES).isAfter(now) ? "00:00"
                : now.minusMinutes(ReminderRules.WINDOW_MINUTES).format(HHMM);

        List<Candidate> candidates = jdbc.query("""
                SELECT u.id, u.name, u.email, g.hours_per_day, g.reminder_time, g.channel,
                       COALESCE(s.streak, 0) AS streak, s.last_goal_date, COALESCE(l.minutes, 0) AS minutes, (u.email_verified_at IS NOT NULL) AS verified
                FROM users u
                JOIN goals g ON g.user_id = u.id
                LEFT JOIN user_stats s ON s.user_id = u.id
                LEFT JOIN study_logs l ON l.user_id = u.id AND l.day = ?
                WHERE g.reminder_time <= ? AND g.reminder_time >= ?
                  AND NOT EXISTS (SELECT 1 FROM reminder_log r WHERE r.user_id = u.id AND r.day = ?)""",
                (rs, i) -> new Candidate(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                        rs.getBigDecimal("hours_per_day").doubleValue(), rs.getString("reminder_time"), rs.getString("channel"),
                        rs.getInt("streak"), rs.getObject("last_goal_date", LocalDate.class), rs.getInt("minutes"), rs.getBoolean("verified")),
                Date.valueOf(today), nowText, earliest, Date.valueOf(today));

        int sent = 0;
        for (Candidate c : candidates) {
            int goalMinutes = (int) Math.round(c.hours() * 60);
            if (!ReminderRules.due(now, LocalTime.parse(c.reminderTime()), c.minutesToday(), goalMinutes, false)) continue;
            // The primary key makes this the single winner if two servers race.
            int claimed = jdbc.update("INSERT INTO reminder_log (user_id, day, sent_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    c.id(), Date.valueOf(today), Timestamp.from(instant));
            if (claimed == 0) continue;
            int streak = StreakCalculator.current(c.lastGoalDate(), today, c.streak());
            ReminderRules.Message m = ReminderRules.message(c.name().split(" ")[0], c.minutesToday(), goalMinutes, streak);
            notifications.add(c.id(), "REMINDER", m.title(), m.body(), instant);
            // Email goes only to addresses the student has proved they own, so a typo never gets our mail.
            if ("EMAIL".equals(c.channel()) && c.emailVerified()) {
                try {
                    email.send(c.email(), m.title(), m.body());
                } catch (RuntimeException e) {
                    log.warn("Reminder email to user {} failed: {}", c.id(), e.getMessage());
                }
            }
            sent++;
        }
        return sent;
    }
}
