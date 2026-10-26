package com.gateready.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Allows at most max calls per window for a key, counting every call. In-memory, like LoginRateLimiter. */
@Component
public class AttemptLimiter {
    private final ConcurrentHashMap<String, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    public AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns true and records the call when it is within the limit, false when the key is over it. */
    public boolean allow(String key, int max, Duration window) {
        Deque<Instant> q = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            Instant now = clock.instant();
            Instant cutoff = now.minus(window);
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.removeFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }
}
