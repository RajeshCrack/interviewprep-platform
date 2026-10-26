package com.gateready.auth;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One-time links for email verification and password reset. Only a hash of each token is stored. */
@Service
public class EmailTokenService {
    public enum Kind { VERIFY, RESET }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EmailTokenService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Creates a token and cancels the user's earlier unused tokens of the same kind, so only the newest link works. */
    public String create(UUID userId, Kind kind, Duration ttl) {
        Instant now = clock.instant();
        jdbc.update("UPDATE email_tokens SET used_at = ? WHERE user_id = ? AND kind = ? AND used_at IS NULL",
                Timestamp.from(now), userId, kind.name());
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO email_tokens (id, user_id, kind, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, kind.name(), AuthService.hash(raw), Timestamp.from(now), Timestamp.from(now.plus(ttl)));
        return raw;
    }

    /** Uses the token once. Returns the user id, or empty when it is unknown, already used, expired or of another kind. */
    public Optional<UUID> consume(String raw, Kind kind) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        // One statement, so two clicks at the same moment cannot both succeed.
        return jdbc.query("""
                UPDATE email_tokens SET used_at = ?
                WHERE token_hash = ? AND kind = ? AND used_at IS NULL AND expires_at > ?
                RETURNING user_id""",
                rs -> rs.next() ? Optional.of(rs.getObject(1, UUID.class)) : Optional.<UUID>empty(),
                Timestamp.from(now), AuthService.hash(raw), kind.name(), Timestamp.from(now));
    }

    public int createdSince(UUID userId, Kind kind, Duration window) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM email_tokens WHERE user_id = ? AND kind = ? AND created_at > ?",
                Integer.class, userId, kind.name(), Timestamp.from(clock.instant().minus(window)));
        return n == null ? 0 : n;
    }
}
