package com.gateready.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gateready.MutableClock;
import com.gateready.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T04:30:00Z"), ZoneId.of("UTC"));

    private JwtService service(String secret) {
        return new JwtService(new AppProperties("UTC", "*", new AppProperties.Jwt(secret, 15, 30)), clock);
    }

    @Test
    void roundTripsTheUserIdAndRole() {
        JwtService jwt = service(SECRET);
        UUID id = UUID.randomUUID();
        var v = jwt.verify(jwt.issue(id, "STUDENT")).orElseThrow();
        assertThat(v.userId()).isEqualTo(id);
        assertThat(v.role()).isEqualTo("STUDENT");
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtService jwt = service(SECRET);
        String token = jwt.issue(UUID.randomUUID(), "STUDENT");
        clock.advance(Duration.ofMinutes(16));
        assertThat(jwt.verify(token)).isEmpty();
    }

    @Test
    void rejectsATamperedToken() {
        JwtService jwt = service(SECRET);
        String token = jwt.issue(UUID.randomUUID(), "STUDENT");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThat(jwt.verify(tampered)).isEmpty();
        assertThat(jwt.verify("not-a-token")).isEmpty();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String token = service("another-secret-another-secret-123456").issue(UUID.randomUUID(), "ADMIN");
        assertThat(service(SECRET).verify(token)).isEmpty();
    }

    @Test
    void refusesToStartWithAWeakOrMissingSecret() {
        assertThatThrownBy(() -> service("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null)).isInstanceOf(IllegalStateException.class);
    }
}
