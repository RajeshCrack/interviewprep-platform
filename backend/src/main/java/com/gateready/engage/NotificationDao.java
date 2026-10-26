package com.gateready.engage;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDao {
    public record Item(UUID id, String kind, String title, String body, Instant createdAt, boolean read) {}

    private final JdbcTemplate jdbc;

    public NotificationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(UUID userId, String kind, String title, String body, Instant at) {
        jdbc.update("INSERT INTO notifications (id, user_id, kind, title, body, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, kind, title, body, Timestamp.from(at));
    }

    public List<Item> latest(UUID userId, int limit) {
        return jdbc.query("SELECT id, kind, title, body, created_at, read_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id LIMIT ?",
                (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("title"), rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null), userId, limit);
    }

    public int unread(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void markAllRead(UUID userId, Instant at) {
        jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL", Timestamp.from(at), userId);
    }
}
