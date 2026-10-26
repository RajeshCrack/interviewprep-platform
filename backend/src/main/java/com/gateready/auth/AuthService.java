package com.gateready.auth;

import com.gateready.auth.AuthDtos.TokenResponse;
import com.gateready.common.ApiException;
import com.gateready.config.AppProperties;
import com.gateready.goal.Channel;
import com.gateready.goal.Goal;
import com.gateready.goal.GoalRepository;
import com.gateready.user.User;
import com.gateready.user.UserRepository;
import com.gateready.user.UserStats;
import com.gateready.user.UserStatsRepository;
import com.gateready.user.UserView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final GoalRepository goals;
    private final UserStatsRepository stats;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter limiter;
    private final Clock clock;
    private final Duration refreshTtl;
    private final String dummyHash;

    public AuthService(UserRepository users, GoalRepository goals, UserStatsRepository stats,
                       RefreshTokenRepository refreshTokens, PasswordEncoder encoder, JwtService jwt,
                       LoginRateLimiter limiter, Clock clock, AppProperties props) {
        this.users = users;
        this.goals = goals;
        this.stats = stats;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
        this.clock = clock;
        this.refreshTtl = Duration.ofDays(props.jwt().refreshDays());
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public TokenResponse register(String name, String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already registered.");
        }
        User user = users.save(new User(name.trim(), normalized, encoder.encode(password)));
        goals.save(new Goal(user.getId(), 2.0, "20:00", Channel.PUSH));
        stats.save(new UserStats(user.getId()));
        return issue(user);
    }

    @Transactional
    public TokenResponse login(String email, String password, String clientKey) {
        String normalized = normalize(email);
        String key = normalized + "|" + clientKey;
        if (limiter.isBlocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again in 15 minutes.");
        }
        User user = users.findByEmail(normalized).orElse(null);
        // Compare against a dummy hash when the email is unknown so timing does not reveal which emails exist.
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            limiter.recordFailure(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        limiter.reset(key);
        return issue(user);
    }

    /** Rotates the refresh token. Presenting a token that was already used revokes every session of that user. */
    // noRollbackFor: the revocation of every session must be kept even though this call ends in a 401.
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again."));
        Instant now = clock.instant();
        if (stored.isRevoked()) {
            refreshTokens.findByUserId(stored.getUserId()).forEach(t -> t.revoke(now));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again.");
        }
        stored.revoke(now);
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in again."));
        return issue(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    private TokenResponse issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), clock.instant().plus(refreshTtl)));
        return new TokenResponse(jwt.issue(user.getId(), user.getRole()), raw, UserView.of(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
