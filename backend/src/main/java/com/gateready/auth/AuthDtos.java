package com.gateready.auth;

import com.gateready.user.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Enter your full name.") @Size(min = 2, max = 80, message = "Name must be 2 to 80 characters.") String name,
            @NotBlank(message = "Enter a valid email address.") @Email(message = "Enter a valid email address.") @Size(max = 254) String email,
            @NotBlank(message = "Use at least 8 characters.") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") String email,
            @NotBlank(message = "Enter your password.") String password) {}

    public record RefreshRequest(@NotBlank(message = "Missing refresh token.") String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, UserView user) {}
}
