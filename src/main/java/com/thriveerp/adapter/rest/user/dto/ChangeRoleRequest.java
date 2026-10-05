package com.thriveerp.adapter.rest.user.dto;

import com.thriveerp.core.domain.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
