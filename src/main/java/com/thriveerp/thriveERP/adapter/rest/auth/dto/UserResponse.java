package com.thriveerp.thriveERP.adapter.rest.auth.dto;

import com.thriveerp.thriveERP.core.domain.user.User;

import java.util.UUID;

// Deliberately excludes passwordHash — never serialize it, even by accident.
public record UserResponse(UUID id, String username, String email, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
    }
}
