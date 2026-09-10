package com.thriveerp.thriveERP.adapter.rest.auth;

import com.thriveerp.thriveERP.adapter.rest.auth.dto.AuthResponse;
import com.thriveerp.thriveERP.adapter.rest.auth.dto.LoginRequest;
import com.thriveerp.thriveERP.adapter.rest.auth.dto.RegisterRequest;
import com.thriveerp.thriveERP.adapter.rest.auth.dto.UserResponse;
import com.thriveerp.thriveERP.core.app.user.AuthService;
import com.thriveerp.thriveERP.core.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auth endpoints. adapter-rest only — translates HTTP <-> domain
 * commands and calls core-app. No business rules live here (ARCHITECTURE.md §3).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ResponseEntity.ok(AuthResponse.bearer(token));
    }
}
