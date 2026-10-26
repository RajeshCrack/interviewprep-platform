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

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(r.name(), r.email(), r.password());
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
}
