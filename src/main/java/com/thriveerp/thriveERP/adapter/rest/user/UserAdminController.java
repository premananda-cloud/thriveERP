package com.thriveerp.thriveERP.adapter.rest.user;

import com.thriveerp.thriveERP.adapter.rest.auth.dto.UserResponse;
import com.thriveerp.thriveERP.adapter.rest.user.dto.ChangeRoleRequest;
import com.thriveerp.thriveERP.core.app.user.AuthService;
import com.thriveerp.thriveERP.core.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Granting privilege" per ARCHITECTURE.md. Deliberately its own controller,
 * separate from AuthController — registration/login are public, role
 * management is admin-only, and keeping them apart makes that boundary obvious.
 */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final AuthService authService;

    public UserAdminController(AuthService authService) {
        this.authService = authService;
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeRole(@PathVariable UUID id,
                                                    @Valid @RequestBody ChangeRoleRequest request) {
        User user = authService.changeRole(id, request.role());
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
