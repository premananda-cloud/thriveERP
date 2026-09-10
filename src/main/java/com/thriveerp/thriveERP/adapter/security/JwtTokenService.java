package com.thriveerp.thriveERP.adapter.security;

import com.thriveerp.thriveERP.core.domain.user.TokenServicePort;
import com.thriveerp.thriveERP.core.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT implementation of TokenServicePort (jjwt 0.13.x). Secret comes from
 * app.jwt.secret, which application.yaml sources from the JWT_SECRET env var
 * — see .env.example. Never hardcode the secret here.
 */
@Component
public class JwtTokenService implements TokenServicePort {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not set. Set the JWT_SECRET environment variable " +
                    "(see .env.example) — do not run with an empty JWT secret.");
        }
        // HS256 requires a key >= 256 bits (32 bytes). Fail fast at startup
        // rather than producing tokens that later fail to verify.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256. Generate one with, " +
                    "e.g., `openssl rand -base64 32`.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationSeconds = expirationSeconds;
    }

    @Override
    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** Returns empty on any parse/signature/expiry failure — callers treat
     *  that as "not authenticated" rather than distinguishing the reason. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
