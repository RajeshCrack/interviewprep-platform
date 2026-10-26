package com.gateready.auth;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController {
    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/me/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resend(@AuthenticationPrincipal UUID userId) {
        accounts.resendVerification(userId);
    }
}
