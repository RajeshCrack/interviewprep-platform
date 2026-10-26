package com.gateready.user;

import java.util.UUID;

public record UserView(UUID id, String name, String email) {
    public static UserView of(User u) {
        return new UserView(u.getId(), u.getName(), u.getEmail());
    }
}
