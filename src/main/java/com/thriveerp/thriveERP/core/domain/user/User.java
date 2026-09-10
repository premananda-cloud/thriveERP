package com.thriveerp.thriveERP.core.domain.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core domain object for a registered user. Pure Java only — no Spring, no
 * JPA, no Jackson (groundrule.txt §2). Adapters translate to/from this shape.
 */
public class User {

    private final UUID id;
    private String username;
    private String email;
    private String passwordHash;
    private Role role;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(UUID id, String username, String email, String passwordHash, Role role,
                Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.username = Objects.requireNonNull(username);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** Factory for a brand-new registration. Defaults to CUSTOMER — role
     *  escalation (STAFF/ADMIN) is a separate, explicit action (see changeRole). */
    public static User newRegistration(String username, String email, String passwordHash) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), username, email, passwordHash, Role.CUSTOMER, now, now);
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
