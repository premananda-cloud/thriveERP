package com.thriveerp.adapter.security;

import com.thriveerp.core.domain.user.UserRepositoryPort;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads "Authorization: Bearer <token>", and if valid, populates the
 * SecurityContext so downstream @PreAuthorize / hasRole checks work.
 * Registered before UsernamePasswordAuthenticationFilter in SecurityConfig.
 *
 * The token only proves WHO the caller is (its userId claim). The caller's
 * CURRENT role is read from the database on every request, never from the
 * token — so a role change or demotion takes effect immediately instead of
 * after the token expires, and a deleted user's token stops working. Cost: one
 * primary-key lookup per authenticated request.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final UserRepositoryPort userRepository;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserRepositoryPort userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<Claims> claims = jwtTokenService.parse(token);

            claims.ifPresent(this::authenticate);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        String rawUserId = claims.get("userId", String.class);
        if (rawUserId == null) {
            return;
        }
        UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Unknown user (e.g. deleted) -> no authentication -> 401 downstream.
        userRepository.findById(userId).ifPresent(user -> {
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
            var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
    }
}
