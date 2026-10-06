package com.thriveerp.adapter.security;

import com.thriveerp.core.domain.user.Role;
import com.thriveerp.core.domain.user.User;
import com.thriveerp.core.domain.user.UserRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins down the "role comes from the database, not the token" rule: a role change
 * or demotion must take effect on the very next request, and a deleted user's
 * still-unexpired token must stop working.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-secret-value-32-bytes-minimum!";

    @Mock UserRepositoryPort userRepository;

    private JwtTokenService tokenService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(SECRET, 3600);
        filter = new JwtAuthenticationFilter(tokenService, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(UUID id, Role role) {
        return new User(id, "alice", "alice@example.com", "hash", role, Instant.now(), Instant.now());
    }

    private void runFilterWithBearer(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    @Test
    void usesCurrentRoleFromDatabase_notTheRoleBakedIntoTheToken_whenUserWasPromoted() throws Exception {
        UUID id = UUID.randomUUID();
        String tokenIssuedWhenCustomer = tokenService.issueToken(user(id, Role.CUSTOMER));
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, Role.ADMIN)));

        runFilterWithBearer(tokenIssuedWhenCustomer);

        assertThat(hasAuthority("ROLE_ADMIN")).isTrue();
        assertThat(hasAuthority("ROLE_CUSTOMER")).isFalse();
    }

    @Test
    void demotionTakesEffectImmediately_evenWithAnUnexpiredAdminToken() throws Exception {
        UUID id = UUID.randomUUID();
        String tokenIssuedWhenAdmin = tokenService.issueToken(user(id, Role.ADMIN));
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, Role.CUSTOMER)));

        runFilterWithBearer(tokenIssuedWhenAdmin);

        assertThat(hasAuthority("ROLE_ADMIN")).isFalse();
        assertThat(hasAuthority("ROLE_CUSTOMER")).isTrue();
    }

    @Test
    void doesNotAuthenticate_whenTheUserNoLongerExists() throws Exception {
        UUID id = UUID.randomUUID();
        String token = tokenService.issueToken(user(id, Role.ADMIN));
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        runFilterWithBearer(token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNotAuthenticate_andSkipsTheDatabase_whenTheTokenIsInvalid() throws Exception {
        runFilterWithBearer("not.a.jwt");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void doesNotAuthenticate_whenThereIsNoAuthorizationHeader() throws Exception {
        runFilterWithBearer(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
    }
}
