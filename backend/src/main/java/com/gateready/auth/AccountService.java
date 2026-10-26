package com.gateready.auth;

import com.gateready.auth.EmailTokenService.Kind;
import com.gateready.common.ApiException;
import com.gateready.engage.EmailSender;
import com.gateready.user.User;
import com.gateready.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification and password reset. Links are one-time, hashed in the database, and sent only by email.
 * Asking for a reset never reveals whether an address has an account.
 */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    static final Duration VERIFY_TTL = Duration.ofHours(48);
    static final Duration RESET_TTL = Duration.ofHours(1);
    static final Duration RESEND_GAP = Duration.ofSeconds(60);
    static final int RESETS_PER_HOUR = 3;
    static final int FORGOT_PER_IP = 10;
    static final Duration FORGOT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final EmailTokenService tokens;
    private final EmailSender email;
    private final PasswordEncoder encoder;
    private final AttemptLimiter limiter;
    private final Clock clock;
    private final String baseUrl;

    public AccountService(UserRepository users, RefreshTokenRepository refreshTokens, EmailTokenService tokens,
                          EmailSender email, PasswordEncoder encoder, AttemptLimiter limiter, Clock clock,
                          @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.email = email;
        this.encoder = encoder;
        this.limiter = limiter;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Sends the verification link. Never throws: a broken mail server must not block registration. */
    public void sendVerification(UUID userId) {
        try {
            User user = users.findById(userId).orElse(null);
            if (user == null || user.isEmailVerified()) return;
            String token = tokens.create(userId, Kind.VERIFY, VERIFY_TTL);
            email.send(user.getEmail(), "Verify your GateReady email",
                    "Hi " + firstName(user) + ",\n\nConfirm your email address so we can send you study reminders:\n"
                            + baseUrl + "/verify-email?token=" + token
                            + "\n\nThe link works once and expires in 48 hours. If you did not create a GateReady account, ignore this email.");
        } catch (RuntimeException e) {
            log.warn("Verification email for user {} failed: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = tokens.consume(rawToken, Kind.VERIFY).orElseThrow(AccountService::invalidLink);
        users.findById(userId).ifPresent(u -> u.markEmailVerified(clock.instant()));
    }

    /** Sends a fresh link to a logged-in student. At most one a minute. */
    public void resendVerification(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in."));
        if (user.isEmailVerified()) return;
        if (tokens.createdSince(userId, Kind.VERIFY, RESEND_GAP) > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "We just sent a link. Wait a minute before asking again.");
        }
        sendVerification(userId);
    }

    /** Always looks the same to the caller, whether or not the address has an account. */
    public void forgotPassword(String rawEmail, String clientKey) {
        if (!limiter.allow("forgot|" + clientKey, FORGOT_PER_IP, FORGOT_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again in a few minutes.");
        }
        users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
            if (tokens.createdSince(user.getId(), Kind.RESET, Duration.ofHours(1)) >= RESETS_PER_HOUR) return;
            try {
                String token = tokens.create(user.getId(), Kind.RESET, RESET_TTL);
                email.send(user.getEmail(), "Reset your GateReady password",
                        "Hi " + firstName(user) + ",\n\nChoose a new password here:\n" + baseUrl + "/reset-password?token=" + token
                                + "\n\nThe link works once and expires in 1 hour. If you did not ask for this, ignore this email; your password has not changed.");
            } catch (RuntimeException e) {
                log.warn("Reset email for user {} failed: {}", user.getId(), e.getMessage());
            }
        });
    }

    /** Sets the new password, signs the student out everywhere, and confirms by email. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = tokens.consume(rawToken, Kind.RESET).orElseThrow(AccountService::invalidLink);
        User user = users.findById(userId).orElseThrow(AccountService::invalidLink);
        user.changePasswordHash(encoder.encode(newPassword));
        user.markEmailVerified(clock.instant()); // they just proved they control the inbox
        refreshTokens.findByUserId(userId).forEach(t -> t.revoke(clock.instant()));
        try {
            email.send(user.getEmail(), "Your GateReady password was changed",
                    "Hi " + firstName(user) + ",\n\nYour password was just changed and you were signed out everywhere. "
                            + "If this was not you, reset it again now using \"Forgot password\" on the login page.");
        } catch (RuntimeException e) {
            log.warn("Password-changed email for user {} failed: {}", userId, e.getMessage());
        }
    }

    private static ApiException invalidLink() {
        return new ApiException(HttpStatus.BAD_REQUEST, "This link is invalid or has expired. Request a new one.");
    }

    private static String firstName(User u) {
        return u.getName().split(" ")[0];
    }
}
