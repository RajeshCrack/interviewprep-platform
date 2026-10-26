package com.gateready.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gateready.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T04:30:00Z"), ZoneId.of("UTC"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    @Test
    void blocksOnTheFifthFailure() {
        for (int i = 0; i < 4; i++) limiter.recordFailure("a");
        assertThat(limiter.isBlocked("a")).isFalse();
        limiter.recordFailure("a");
        assertThat(limiter.isBlocked("a")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) limiter.recordFailure("a");
        assertThat(limiter.isBlocked("b")).isFalse();
    }

    @Test
    void failuresExpireAfterFifteenMinutes() {
        for (int i = 0; i < 5; i++) limiter.recordFailure("a");
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(limiter.isBlocked("a")).isFalse();
    }

    @Test
    void successResetsTheCount() {
        for (int i = 0; i < 5; i++) limiter.recordFailure("a");
        limiter.reset("a");
        assertThat(limiter.isBlocked("a")).isFalse();
    }
}
