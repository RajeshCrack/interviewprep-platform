package com.gateready.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.gateready.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AttemptLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);
    private final AttemptLimiter limiter = new AttemptLimiter(clock);
    private final Duration window = Duration.ofMinutes(15);

    @Test
    void allowsUpToTheMaximumThenBlocks() {
        for (int i = 0; i < 3; i++) assertThat(limiter.allow("k", 3, window)).isTrue();
        assertThat(limiter.allow("k", 3, window)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) limiter.allow("a", 3, window);
        assertThat(limiter.allow("a", 3, window)).isFalse();
        assertThat(limiter.allow("b", 3, window)).isTrue();
    }

    @Test
    void callsExpireWhenTheWindowPasses() {
        for (int i = 0; i < 3; i++) limiter.allow("k", 3, window);
        clock.set(clock.instant().plus(window).plusSeconds(1));
        assertThat(limiter.allow("k", 3, window)).isTrue();
    }

    @Test
    void blockedCallsDoNotExtendTheBlock() {
        for (int i = 0; i < 3; i++) limiter.allow("k", 3, window);
        clock.set(clock.instant().plus(Duration.ofMinutes(10)));
        assertThat(limiter.allow("k", 3, window)).isFalse();
        clock.set(clock.instant().plus(Duration.ofMinutes(6)));
        assertThat(limiter.allow("k", 3, window)).isTrue();
    }
}
