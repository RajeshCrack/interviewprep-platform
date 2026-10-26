package com.gateready.auth;

import com.gateready.auth.AuthDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    private final AccountService accounts;

    public AuthController(AuthService auth, AccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody RegisterRequest r) {
        TokenResponse t = auth.register(r.name(), r.email(), r.password());
        accounts.sendVerification(t.user().id()); // after the account is saved; a mail failure is logged, not shown
        return t;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest http) {
        return auth.login(r.email(), r.password(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return auth.refresh(r.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest r) {
        auth.logout(r.refreshToken());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody TokenRequest r) {
        accounts.verifyEmail(r.token());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse forgot(@Valid @RequestBody ForgotRequest r, HttpServletRequest http) {
        accounts.forgotPassword(r.email(), http.getRemoteAddr());
        return new MessageResponse("If that email is registered, we sent a link to reset the password.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody ResetRequest r) {
        accounts.resetPassword(r.token(), r.newPassword());
    }
}
