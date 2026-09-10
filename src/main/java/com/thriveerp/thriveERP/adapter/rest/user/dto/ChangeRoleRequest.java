package com.thriveerp.thriveERP.adapter.rest.user.dto;

import com.thriveerp.thriveERP.core.domain.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
