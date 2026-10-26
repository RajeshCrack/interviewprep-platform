package com.gateready.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;
    private String name;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String role;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    protected User() {}

    public User(String name, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = "STUDENT";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public void markEmailVerified(Instant at) { if (emailVerifiedAt == null) emailVerifiedAt = at; }
    public void changePasswordHash(String hash) { this.passwordHash = hash; }
}
