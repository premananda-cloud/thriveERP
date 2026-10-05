package com.thriveerp.adapter.security;

import com.thriveerp.core.domain.user.Role;
import com.thriveerp.core.domain.user.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    // 37 bytes — comfortably over the 32-byte HS256 minimum the class enforces.
    private static final String VALID_SECRET = "unit-test-secret-value-32-bytes-min!";

    @Test
    void issueToken_thenParse_roundTripsUsernameAndRole() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 3600);
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash",
                Role.STAFF, Instant.now(), Instant.now());

        String token = service.issueToken(user);
        Optional<Claims> claims = service.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("alice");
        assertThat(claims.get().get("role", String.class)).isEqualTo("STAFF");
        assertThat(claims.get().get("userId", String.class)).isEqualTo(user.getId().toString());
    }

    @Test
    void parse_returnsEmpty_forTamperedToken() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 3600);
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        String token = service.issueToken(user);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertThat(service.parse(tampered)).isEmpty();
    }

    @Test
    void parse_returnsEmpty_forExpiredToken() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, -10); // already expired at issuance
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());
        String token = service.issueToken(user);

        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    void parse_returnsEmpty_forGarbageInput() {
        JwtTokenService service = new JwtTokenService(VALID_SECRET, 3600);
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void tokensSignedWithDifferentSecrets_doNotParseAgainstEachOther() {
        JwtTokenService issuer = new JwtTokenService(VALID_SECRET, 3600);
        JwtTokenService verifier = new JwtTokenService("a-completely-different-secret-32-bytes!", 3600);
        User user = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash",
                Role.CUSTOMER, Instant.now(), Instant.now());

        String token = issuer.issueToken(user);

        assertThat(verifier.parse(token)).isEmpty();
    }

    @Test
    void constructor_rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtTokenService("", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void constructor_rejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtTokenService("too-short", 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
